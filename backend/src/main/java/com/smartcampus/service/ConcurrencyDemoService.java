package com.smartcampus.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.smartcampus.dto.ConcurrencyTestRequest;
import com.smartcampus.dto.ConcurrencyTestResult;
import com.smartcampus.exception.InvalidBookingException;
import com.smartcampus.exception.SmartCampusException;
import com.smartcampus.model.Booking;
import com.smartcampus.model.enums.ResourceType;
import com.smartcampus.repository.BookingRepository;
import com.smartcampus.repository.ClassroomRepository;
import com.smartcampus.repository.EquipmentRepository;
import com.smartcampus.repository.LaboratoryRepository;
import com.smartcampus.security.AuthContext;
import com.smartcampus.security.AuthenticatedUser;

/**
 * Live demonstration of <b>multithreading + synchronisation</b>.
 *
 * <p>The endpoint behind this service starts {@code N} real Java threads at
 * almost the same instant.  Every thread tries to book the <b>same</b>
 * resource for the <b>same</b> time window.  Because
 * {@code BookingServiceImpl} guards the critical section with a
 * {@code ReentrantLock}, exactly one thread wins and the rest receive a
 * {@code BookingConflictException}.</p>
 *
 * <pre>
 *   Thread-1 --&gt; lock()  --&gt; free? --&gt; INSERT  --&gt; OK      (BK-0011)
 *   Thread-2 --&gt; lock()  ... waits ...
 *   Thread-3 --&gt; lock()  ... waits ...
 *   Thread-2 --&gt; granted  --&gt; conflict --&gt; REJECTED  (409)
 *   Thread-3 --&gt; granted  --&gt; conflict --&gt; REJECTED  (409)
 * </pre>
 *
 * <p><b>Syllabus concepts:</b> {@code Thread}, {@code Runnable},
 * {@code CountDownLatch} (thread barrier), {@code CompletableFuture}-style
 * coordination through {@code ExecutorService}, custom exceptions and the
 * synchronised booking service.</p>
 */
@Service
public class ConcurrencyDemoService {

    private static final Logger log = LoggerFactory.getLogger(ConcurrencyDemoService.class);

    private static final int MIN_ATTEMPTS = 2;
    private static final int MAX_ATTEMPTS = 20;
    private static final long LATCH_TIMEOUT_SECONDS = 60;

    private final BookingService bookingService;
    private final BookingRepository bookingRepository;
    private final ClassroomRepository classroomRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final EquipmentRepository equipmentRepository;

    public ConcurrencyDemoService(BookingService bookingService,
                                  BookingRepository bookingRepository,
                                  ClassroomRepository classroomRepository,
                                  LaboratoryRepository laboratoryRepository,
                                  EquipmentRepository equipmentRepository) {
        this.bookingService = bookingService;
        this.bookingRepository = bookingRepository;
        this.classroomRepository = classroomRepository;
        this.laboratoryRepository = laboratoryRepository;
        this.equipmentRepository = equipmentRepository;
    }

    /**
     * Fires {@code attempts} threads that all compete for one time slot.
     *
     * @return a report showing exactly one success and {@code attempts - 1}
     *         conflicts (unless the slot was already taken beforehand)
     */
    public ConcurrencyTestResult run(ConcurrencyTestRequest request) {
        validate(request);

        ConcurrencyTestResult result = new ConcurrencyTestResult();
        int attempts = Math.min(Math.max(request.getAttempts(), MIN_ATTEMPTS), MAX_ATTEMPTS);
        result.setAttempts(attempts);
        result.setResourceLabel(resolveLabel(request));

        // one thread per attempt, released simultaneously by a barrier
        CountDownLatch startLine = new CountDownLatch(1);
        CountDownLatch finishLine = new CountDownLatch(attempts);
        List<String> outcomes = new ArrayList<>();
        int[] succeeded = {0};
        int[] failed = {0};

        long startedAt = System.currentTimeMillis();

        /*
         * AuthContext is a ThreadLocal, so the worker threads below start out
         * "anonymous".  The logged-in user is captured ONCE here and installed
         * inside every thread - without it BookingServiceImpl would (correctly)
         * reject each attempt with 401 and the race would prove nothing.
         */
        final AuthenticatedUser caller = AuthContext.get();

        List<Thread> threads = new ArrayList<>();
        for (int index = 1; index <= attempts; index++) {
            final String threadName = "BookingThread-" + index;

            Thread thread = new Thread(() -> {
                AuthContext.set(caller);
                try {
                    // every thread waits here until the start line drops
                    if (!startLine.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                        return;
                    }
                    Booking saved = bookingService.create(copyOf(request), false);
                    synchronized (outcomes) {
                        succeeded[0]++;
                        outcomes.add(threadName + ": SUCCESS - created " + saved.getBookingRef()
                                + " for " + saved.getResourceName() + " "
                                + saved.getStartTime() + "-" + saved.getEndTime());
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    synchronized (outcomes) {
                        outcomes.add(threadName + ": INTERRUPTED");
                    }
                } catch (Exception ex) {
                    // the expected outcome for every losing thread
                    synchronized (outcomes) {
                        failed[0]++;
                        outcomes.add(threadName + ": REJECTED - " + ex.getMessage());
                    }
                } finally {
                    AuthContext.clear();
                    finishLine.countDown();
                }
            }, threadName);

            threads.add(thread);
            thread.start();
        }

        try {
            startLine.countDown();                       // release all threads at once
            if (!finishLine.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new SmartCampusException(
                        "The concurrency test timed out waiting for the worker threads.");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new SmartCampusException("The concurrency test was interrupted.", ex);
        }

        long elapsed = System.currentTimeMillis() - startedAt;

        result.setSucceeded(succeeded[0]);
        result.setFailed(failed[0]);
        result.setElapsedMillis(elapsed);
        result.setOutcomes(outcomes);
        result.setSummary(summarise(result));

        log.info("Concurrency test on {} finished: {} succeeded, {} rejected in {} ms",
                result.getResourceLabel(), succeeded[0], failed[0], elapsed);

        return result;
    }

    // ------------------------------------------------------------------

    private String summarise(ConcurrencyTestResult result) {
        if (result.getSucceeded() == 1 && result.getFailed() == result.getAttempts() - 1) {
            return String.format(
                    "%d threads raced for the same slot: exactly 1 booking was created and "
                            + "%d were rejected. The synchronised critical section prevented a double booking.",
                    result.getAttempts(), result.getFailed());
        }
        if (result.getSucceeded() == 0) {
            return String.format(
                    "All %d threads were rejected because the slot was already occupied "
                            + "before the test started. No double booking occurred.",
                    result.getAttempts());
        }
        return String.format("%d of %d threads succeeded. Review the outcomes below.",
                result.getSucceeded(), result.getAttempts());
    }

    private void validate(ConcurrencyTestRequest request) {
        if (request.getResourceType() == null) {
            throw new InvalidBookingException("resource type is required");
        }
        if (request.getResourceId() == null) {
            throw new InvalidBookingException("resource id is required");
        }
        if (request.getDate() == null || request.getDate().isBefore(LocalDate.now())) {
            throw new InvalidBookingException("date must be today or in the future");
        }
        if (request.getStartTime() == null || request.getEndTime() == null) {
            throw new InvalidBookingException("start time and end time are required");
        }
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new InvalidBookingException("end time must be after start time");
        }
        if (request.getAttempts() < MIN_ATTEMPTS) {
            throw new InvalidBookingException(
                    "attempts must be at least " + MIN_ATTEMPTS);
        }
    }

    /** Uses the real repositories (not the service) so no extra audit noise is created. */
    private String resolveLabel(ConcurrencyTestRequest request) {
        ResourceType type = request.getResourceType();
        Long id = request.getResourceId();

        return switch (type) {
            case CLASSROOM -> classroomRepository.findById(id)
                    .map(room -> room.getResourceLabel() + " (" + type.getLabel() + ")")
                    .orElseThrow(() -> new com.smartcampus.exception.ResourceNotFoundException(type, id));
            case LABORATORY -> laboratoryRepository.findById(id)
                    .map(lab -> lab.getResourceLabel() + " (" + type.getLabel() + ")")
                    .orElseThrow(() -> new com.smartcampus.exception.ResourceNotFoundException(type, id));
            case EQUIPMENT -> equipmentRepository.findById(id)
                    .map(item -> item.getResourceLabel() + " (" + type.getLabel() + ")")
                    .orElseThrow(() -> new com.smartcampus.exception.ResourceNotFoundException(type, id));
        };
    }

    /** Independent copy so each thread owns its own object (no shared mutable state). */
    private Booking copyOf(ConcurrencyTestRequest request) {
        Booking booking = new Booking();
        booking.setResourceType(request.getResourceType());
        booking.setResourceId(request.getResourceId());
        booking.setBookingDate(request.getDate());
        booking.setStartTime(request.getStartTime());
        booking.setEndTime(request.getEndTime());
        booking.setPurpose(request.getPurpose());
        return booking;
    }

    /** Ids of the resources offered in the demo drop-down (one per type). */
    public List<ResourceOption> sampleResources() {
        List<ResourceOption> options = new ArrayList<>();
        classroomRepository.findAll().forEach(room -> options.add(
                new ResourceOption(ResourceType.CLASSROOM, room.getId(), room.getResourceLabel())));
        laboratoryRepository.findAll().forEach(lab -> options.add(
                new ResourceOption(ResourceType.LABORATORY, lab.getId(), lab.getResourceLabel())));
        equipmentRepository.findAll().forEach(item -> options.add(
                new ResourceOption(ResourceType.EQUIPMENT, item.getId(), item.getResourceLabel())));
        return options;
    }

    /** Tiny value object used by the demo drop-down. */
    public record ResourceOption(ResourceType type, Long id, String label) {
    }

    /** Number of bookings currently in the table (shown after the test). */
    public long bookingCount() {
        return bookingRepository.count();
    }
}
