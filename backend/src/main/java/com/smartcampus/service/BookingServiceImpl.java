package com.smartcampus.service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.smartcampus.dto.AvailabilityRequest;
import com.smartcampus.dto.AvailabilityResponse;
import com.smartcampus.dto.PagedResponse;
import com.smartcampus.dto.SearchCriteria;
import com.smartcampus.exception.BookingConflictException;
import com.smartcampus.exception.ForbiddenException;
import com.smartcampus.exception.InvalidBookingException;
import com.smartcampus.exception.ResourceNotFoundException;
import com.smartcampus.exception.UnauthorizedException;
import com.smartcampus.exception.UserNotFoundException;
import com.smartcampus.model.Booking;
import com.smartcampus.model.Resource;
import com.smartcampus.model.UserAccount;
import com.smartcampus.model.enums.BookingStatus;
import com.smartcampus.model.enums.ResourceType;
import com.smartcampus.model.enums.Role;
import com.smartcampus.repository.BookingRepository;
import com.smartcampus.repository.ClassroomRepository;
import com.smartcampus.repository.EquipmentRepository;
import com.smartcampus.repository.LaboratoryRepository;
import com.smartcampus.repository.UserRepository;
import com.smartcampus.security.AuthContext;
import com.smartcampus.security.AuthenticatedUser;
import com.smartcampus.utility.TransactionExecutor;

/**
 * Resource booking with <b>concurrent access protection</b>.
 *
 * <h2>Why is a lock needed?</h2>
 * <p>Two users can ask for the same laboratory at the same millisecond.
 * If both threads ran the "is it free?" check before either of them inserted,
 * the check would pass for both and the slot would be booked twice:</p>
 *
 * <pre>
 *   Thread A: SELECT  -&gt; free
 *   Thread B: SELECT  -&gt; free            (A has not inserted yet)
 *   Thread A: INSERT  -&gt; BK-0011
 *   Thread B: INSERT  -&gt; BK-0012         &lt;-- DOUBLE BOOKING
 * </pre>
 *
 * <h2>How this class prevents it</h2>
 * <ol>
 *   <li><b>{@code ReentrantLock}</b> - one lock per
 *       {@code resource#date}.  The whole "check + insert" critical section
 *       runs while that lock is held, so a second thread for the same room on
 *       the same day must wait until the first one has finished.
 *       Different rooms still run in parallel.</li>
 *   <li><b>{@code synchronized}</b> - {@link #nextBookingRef()} is a
 *       synchronised method so two threads can never mint the same booking
 *       reference.</li>
 *   <li><b>Transaction</b> - the SELECT and the INSERT share one JDBC
 *       connection, so they either both succeed or both roll back.</li>
 * </ol>
 *
 * <pre>
 *   // Critical section:
 *   // Prevents two users from booking the same resource simultaneously.
 * </pre>
 */
@Service
public class BookingServiceImpl implements BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingServiceImpl.class);

    /** Bookings must last at least this long. */
    private static final Duration MIN_DURATION = Duration.ofMinutes(15);

    /** ... and at most this long (a whole working day). */
    private static final Duration MAX_DURATION = Duration.ofHours(12);

    private final BookingRepository bookingRepository;
    private final ClassroomRepository classroomRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final EquipmentRepository equipmentRepository;
    private final UserRepository userRepository;
    private final TransactionExecutor transactionExecutor;
    private final AuditLogService auditLog;

    /**
     * <b>Lock striping:</b> instead of one global lock (which would serialise
     * every booking in the system) we keep a small map of locks, one per
     * {@code resourceType#resourceId#date}.  Requests for different rooms
     * proceed concurrently; requests for the same room queue up.
     */
    private final ConcurrentMap<String, ReentrantLock> lockRegistry = new ConcurrentHashMap<>();

    /** Guarded by {@code synchronized} - see {@link #nextBookingRef()}. */
    private long bookingSequence;

    public BookingServiceImpl(BookingRepository bookingRepository,
                              ClassroomRepository classroomRepository,
                              LaboratoryRepository laboratoryRepository,
                              EquipmentRepository equipmentRepository,
                              UserRepository userRepository,
                              TransactionExecutor transactionExecutor,
                              AuditLogService auditLog) {
        this.bookingRepository = bookingRepository;
        this.classroomRepository = classroomRepository;
        this.laboratoryRepository = laboratoryRepository;
        this.equipmentRepository = equipmentRepository;
        this.userRepository = userRepository;
        this.transactionExecutor = transactionExecutor;
        this.auditLog = auditLog;
        // start counting after the highest reference already in the database
        this.bookingSequence = bookingRepository.maxBookingId();
    }

    // ==================================================================
    //  Booking creation - the concurrency-critical operation
    // ==================================================================

    @Override
    public Booking create(Booking request) {
        return create(request, false);
    }

    @Override
    public Booking create(Booking request, boolean autoApprove) {
        validate(request);
        resolveRequester(request);
        Resource resource = loadResource(request.getResourceType(), request.getResourceId());

        if (!resource.isBookable()) {
            throw ResourceNotFoundException.notBookable(
                    resource.getResourceLabel(), resource.getStatus().getLabel());
        }
        // snapshot of the resource label so history survives renames
        request.setResourceName(resource.getResourceLabel());
        request.setStatus(autoApprove ? BookingStatus.APPROVED : BookingStatus.PENDING);
        request.setBookingRef(nextBookingRef());

        return bookSafely(request);
    }

    /**
     * THE CRITICAL SECTION.
     *
     * <p>Locks the resource/date, then runs "look for an overlap, insert if
     * free" as one atomic unit.</p>
     *
     * <pre>
     * // Critical section:
     * // Prevents two users from booking the same resource simultaneously.
     * </pre>
     */
    private Booking bookSafely(Booking request) {
        String lockKey = request.getResourceType() + "#"
                + request.getResourceId() + "#"
                + request.getBookingDate();

        ReentrantLock lock = lockRegistry.computeIfAbsent(lockKey, key -> new ReentrantLock());

        lock.lock();                                   // <<< acquire the resource lock
        try {
            return transactionExecutor.doInTransaction(connection -> {
                // 1. is somebody already using this slot? (same connection!)
                Optional<Booking> conflict = bookingRepository.findConflict(
                        connection,
                        request.getResourceType(),
                        request.getResourceId(),
                        request.getBookingDate(),
                        request.getStartTime(),
                        request.getEndTime(),
                        null);

                if (conflict.isPresent()) {
                    Booking existing = conflict.get();
                    log.info("Rejected {} - slot taken by {}",
                            request.getBookingRef(), existing.getBookingRef());
                    // rolls the transaction back and returns HTTP 409 to the client
                    throw new BookingConflictException(
                            request.getResourceType(),
                            existing.getResourceName(),
                            existing.getBookingRef(),
                            existing.getStartTime(),
                            existing.getEndTime());
                }

                // 2. free -> persist within the same transaction
                Booking saved = bookingRepository.save(connection, request);
                log.info("Created {} for {} at {}", saved.getBookingRef(),
                        saved.getResourceName(), saved.getBookingDate());
                return saved;
            });
        } finally {
            lock.unlock();                             // <<< always release, even on error
        }
    }

    /**
     * Generates {@code BK-0001}, {@code BK-0002}, ...
     *
     * <p><b>{@code synchronized}</b>: several threads may call this at the
     * same time; the monitor guarantees that the read-increment-write sequence
     * cannot be interleaved, so no two bookings receive the same reference.</p>
     */
    private synchronized String nextBookingRef() {
        bookingSequence++;
        return String.format("BK-%04d", bookingSequence);
    }

    // ==================================================================
    //  Availability check (read only - no lock needed)
    // ==================================================================

    @Override
    public AvailabilityResponse checkAvailability(AvailabilityRequest request) {
        if (request == null || request.getResourceType() == null || request.getResourceId() == null) {
            throw new InvalidBookingException("resource is required");
        }
        if (request.getDate() == null) {
            throw new InvalidBookingException("date is required");
        }
        if (request.getStartTime() == null || request.getEndTime() == null) {
            throw new InvalidBookingException("start time and end time are required");
        }
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new InvalidBookingException("end time must be after start time");
        }

        Resource resource = loadResource(request.getResourceType(), request.getResourceId());
        if (!resource.isBookable()) {
            return AvailabilityResponse.busy(
                    resource.getResourceLabel() + " is not available for booking "
                            + "(status: " + resource.getStatus().getLabel() + ")",
                    null, null, null);
        }

        Optional<Booking> conflict = bookingRepository.findConflict(
                request.getResourceType(), request.getResourceId(),
                request.getDate(), request.getStartTime(), request.getEndTime(), null);

        if (conflict.isPresent()) {
            Booking existing = conflict.get();
            return AvailabilityResponse.busy(
                    resource.getResourceLabel() + " is already booked from "
                            + existing.getStartTime() + " to " + existing.getEndTime()
                            + " (" + existing.getBookingRef() + ")",
                    existing.getBookingRef(), existing.getStartTime(), existing.getEndTime());
        }

        return AvailabilityResponse.free(resource.getResourceLabel()
                + " is free on " + request.getDate() + " from "
                + request.getStartTime() + " to " + request.getEndTime());
    }

    // ==================================================================
    //  Status transitions
    // ==================================================================

    @Override
    public Booking changeStatus(Long id, BookingStatus status) {
        Booking booking = getById(id);

        if (status == null) {
            throw new InvalidBookingException("status is required");
        }
        if (booking.getStatus() == BookingStatus.COMPLETED) {
            throw new InvalidBookingException(
                    "booking " + booking.getBookingRef() + " is already completed");
        }
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new InvalidBookingException(
                    "booking " + booking.getBookingRef() + " was cancelled - create a new one");
        }
        if (status == BookingStatus.COMPLETED && booking.getStatus() == BookingStatus.PENDING) {
            throw new InvalidBookingException(
                    "a pending booking must be approved before it can be completed");
        }

        booking.setStatus(status);
        bookingRepository.update(booking);

        auditLog.record("STATUS", "BOOKING",
                booking.getBookingRef() + " -> " + status.getLabel());
        return booking;
    }

    @Override
    public Booking cancel(Long id) {
        Booking booking = getVisible(id);          // enforces ownership

        if (booking.getStatus() == BookingStatus.CANCELLED
                || booking.getStatus() == BookingStatus.COMPLETED) {
            throw new InvalidBookingException(
                    "booking " + booking.getBookingRef() + " can no longer be cancelled "
                            + "(status: " + booking.getStatus().getLabel() + ")");
        }

        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.update(booking);

        auditLog.record("CANCEL", "BOOKING",
                "Cancelled " + booking.getBookingRef() + " (" + booking.getResourceName() + ")");
        return booking;
    }

    // ==================================================================
    //  Manageable - CRUD
    // ==================================================================

    @Override
    public PagedResponse<Booking> list(SearchCriteria criteria) {
        criteria.normalise();

        // a student may never read somebody else's bookings
        AuthenticatedUser current = AuthContext.get();
        if (current != null && current.role() == Role.STUDENT) {
            criteria.getFilters().put("requestedBy", String.valueOf(current.id()));
        }

        List<Booking> content = bookingRepository.search(criteria);
        long total = bookingRepository.count(criteria);
        return PagedResponse.of(content, criteria, total);
    }

    @Override
    public Booking getById(Long id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(id));
    }

    @Override
    public Booking getVisible(Long id) {
        Booking booking = getById(id);
        AuthenticatedUser current = AuthContext.get();

        if (current != null && current.role() == Role.STUDENT
                && !Long.valueOf(current.id()).equals(booking.getRequestedBy())) {
            throw new ForbiddenException("You may only view or cancel your own bookings.");
        }
        return booking;
    }

    @Override
    public Booking update(Long id, Booking entity) {
        // bookings are created through create() and moved with changeStatus()
        throw new InvalidBookingException(
                "bookings cannot be edited - cancel the booking and create a new one");
    }

    @Override
    public void delete(Long id) {
        Booking booking = getById(id);
        if (!bookingRepository.deleteById(id)) {
            throw new ResourceNotFoundException(id);
        }
        auditLog.record("DELETE", "BOOKING",
                "Deleted " + booking.getBookingRef() + " (" + booking.getResourceName() + ")");
    }

    // ==================================================================
    //  Searchable
    // ==================================================================

    @Override
    public List<Booking> search(String keyword) {
        return search(keyword, SearchCriteria.MAX_PAGE_SIZE);
    }

    @Override
    public List<Booking> search(String keyword, int maxResults) {
        SearchCriteria criteria = new SearchCriteria(keyword, 0, Math.max(1, maxResults));
        AuthenticatedUser current = AuthContext.get();
        if (current != null && current.role() == Role.STUDENT) {
            criteria.getFilters().put("requestedBy", String.valueOf(current.id()));
        }
        return bookingRepository.search(criteria);
    }

    @Override
    public List<Booking> myBookings() {
        Long userId = AuthContext.userId();
        return bookingRepository.findByUser(userId, 200);
    }

    // ==================================================================
    //  Validation and helpers
    // ==================================================================

    /** Step 3-7 of the booking validation sequence. */
    private void validate(Booking request) {
        if (request == null) {
            throw new InvalidBookingException("request body is required");
        }
        if (request.getResourceType() == null) {
            throw new InvalidBookingException("resource type is required",
                    "Choose a classroom, laboratory or equipment.");
        }
        if (request.getResourceId() == null) {
            throw new InvalidBookingException("resource id is required");
        }
        if (request.getBookingDate() == null) {
            throw new InvalidBookingException("booking date is required");
        }
        if (request.getBookingDate().isBefore(LocalDate.now())) {
            throw new InvalidBookingException("booking date cannot be in the past",
                    "Choose today or a future date.");
        }
        if (request.getStartTime() == null) {
            throw new InvalidBookingException("start time is required");
        }
        if (request.getEndTime() == null) {
            throw new InvalidBookingException("end time is required");
        }
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new InvalidBookingException("end time must be after start time",
                    "The end time must be later than the start time.");
        }

        Duration duration = Duration.between(request.getStartTime(), request.getEndTime());
        if (duration.compareTo(MIN_DURATION) < 0) {
            throw new InvalidBookingException(
                    "a booking must last at least " + MIN_DURATION.toMinutes() + " minutes");
        }
        if (duration.compareTo(MAX_DURATION) > 0) {
            throw new InvalidBookingException(
                    "a booking cannot last longer than " + MAX_DURATION.toHours() + " hours");
        }

        if (request.getPurpose() == null || request.getPurpose().isBlank()) {
            throw new InvalidBookingException("purpose is required",
                    "Tell others why you need this resource.");
        }
        if (request.getPurpose().length() > 255) {
            throw new InvalidBookingException("purpose must be at most 255 characters");
        }
        request.setPurpose(request.getPurpose().trim());

        // the requester always comes from the authenticated session
        if (AuthContext.get() == null) {
            throw new UnauthorizedException("Authentication required. Please sign in.");
        }
    }

    /**
     * Step 1-2 of the booking validation sequence: the resource must exist and
     * must be ACTIVE.
     *
     * <p>Note the return type: a single {@link Resource} reference that points
     * at a {@link Classroom}, {@link Laboratory} or {@link Equipment}.  The
     * calls below ({@code isBookable()}, {@code getResourceLabel()},
     * {@code getStatus()}) are dispatched to the correct overridden method at
     * run time - <b>runtime polymorphism</b>.</p>
     */
    private Resource loadResource(ResourceType type, Long id) {
        return switch (type) {
            case CLASSROOM -> classroomRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException(ResourceType.CLASSROOM, id));
            case LABORATORY -> laboratoryRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException(ResourceType.LABORATORY, id));
            case EQUIPMENT -> equipmentRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException(ResourceType.EQUIPMENT, id));
        };
    }

    /**
     * Never trusts {@code requestedBy} from the request body: a student can
     * only book for him/herself; an administrator may book on behalf of
     * somebody else.
     */
    private void resolveRequester(Booking request) {
        AuthenticatedUser current = AuthContext.get();
        if (current == null) {
            throw new UnauthorizedException("Authentication required. Please sign in.");
        }

        if (current.isAdmin() && request.getRequestedBy() != null) {
            UserAccount account = userRepository.findById(request.getRequestedBy())
                    .orElseThrow(() -> new UserNotFoundException(request.getRequestedBy()));
            request.setRequestedBy(account.getId());
            request.setRequestedByName(account.getFullName());
        } else {
            request.setRequestedBy(current.id());
            request.setRequestedByName(current.fullName());
        }
    }
}
