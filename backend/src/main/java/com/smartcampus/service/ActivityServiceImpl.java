package com.smartcampus.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import com.smartcampus.dto.PagedResponse;
import com.smartcampus.dto.SearchCriteria;
import com.smartcampus.exception.ActivityNotFoundException;
import com.smartcampus.exception.InvalidInputException;
import com.smartcampus.model.CampusActivity;
import com.smartcampus.model.enums.ActivityCategory;
import com.smartcampus.model.enums.ActivityStatus;
import com.smartcampus.repository.ActivityRepository;
import com.smartcampus.utility.Validators;

/**
 * Campus activity business logic: validation, paging, upcoming-event queries
 * and audit logging.
 */
@Service
public class ActivityServiceImpl implements ActivityService {

    private final ActivityRepository repository;
    private final AuditLogService auditLog;

    public ActivityServiceImpl(ActivityRepository repository, AuditLogService auditLog) {
        this.repository = repository;
        this.auditLog = auditLog;
    }

    // ==================================================================
    //  Manageable - CRUD
    // ==================================================================

    @Override
    public PagedResponse<CampusActivity> list(SearchCriteria criteria) {
        criteria.normalise();
        List<CampusActivity> content = repository.search(criteria);
        long total = repository.count(criteria);
        return PagedResponse.of(content, criteria, total);
    }

    @Override
    public CampusActivity getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ActivityNotFoundException(id));
    }

    @Override
    public CampusActivity create(CampusActivity activity) {
        validate(activity);

        if (activity.getStatus() == null) {
            activity.setStatus(ActivityStatus.SCHEDULED);
        }
        CampusActivity saved = repository.save(activity);

        auditLog.record("CREATE", "ACTIVITY",
                "Created activity '" + saved.getActivityName() + "' on " + saved.getStartDate());
        return saved;
    }

    @Override
    public CampusActivity update(Long id, CampusActivity activity) {
        getById(id);
        activity.setId(id);
        validate(activity);

        CampusActivity saved = repository.update(activity);
        auditLog.record("UPDATE", "ACTIVITY",
                "Updated activity '" + saved.getActivityName() + "'");
        return saved;
    }

    @Override
    public void delete(Long id) {
        CampusActivity existing = getById(id);
        if (!repository.deleteById(id)) {
            throw new ActivityNotFoundException(id);
        }
        auditLog.record("DELETE", "ACTIVITY",
                "Deleted activity '" + existing.getActivityName() + "'");
    }

    // ==================================================================
    //  Searchable
    // ==================================================================

    @Override
    public List<CampusActivity> search(String keyword) {
        return search(keyword, SearchCriteria.MAX_PAGE_SIZE);
    }

    @Override
    public List<CampusActivity> search(String keyword, int maxResults) {
        return repository.search(new SearchCriteria(keyword, 0, Math.max(1, maxResults)));
    }

    // ==================================================================
    //  Activity specific operations
    // ==================================================================

    @Override
    public List<CampusActivity> upcoming(int limit) {
        return repository.findUpcoming(Math.max(1, limit));
    }

    @Override
    public long countUpcoming() {
        return repository.countUpcoming();
    }

    @Override
    public List<CampusActivity> onDate(LocalDate day) {
        LocalDate target = day == null ? LocalDate.now() : day;
        return repository.findBetween(target, target);
    }

    @Override
    public List<String> organizers() {
        return repository.findDistinctOrganizers();
    }

    // ==================================================================
    //  Validation
    // ==================================================================

    private void validate(CampusActivity activity) {
        activity.setActivityName(Validators.requiredText(
                activity.getActivityName(), "Activity name", 150));
        activity.setDescription(Validators.optionalText(
                activity.getDescription(), "Description", 1000));
        activity.setOrganizer(Validators.requiredText(
                activity.getOrganizer(), "Organizer", 120));
        activity.setVenue(Validators.requiredText(activity.getVenue(), "Venue", 120));

        if (activity.getCategory() == null) {
            activity.setCategory(ActivityCategory.OTHER);
        }

        LocalDate start = Validators.date(activity.getStartDate(), "Start date");
        LocalDate end = Validators.date(activity.getEndDate(), "End date");
        if (end.isBefore(start)) {
            throw new InvalidInputException("End date", "cannot be before the start date");
        }

        if (activity.getStartTime() == null) {
            throw new InvalidInputException("Start time", "is required");
        }
        if (activity.getEndTime() == null) {
            throw new InvalidInputException("End time", "is required");
        }
        if (!activity.getEndTime().isAfter(activity.getStartTime())) {
            throw new InvalidInputException("End time", "must be after the start time");
        }

        Validators.atLeast(activity.getMaxParticipants(), 1, "Maximum participants");
        Validators.range(activity.getMaxParticipants(), 1, 100_000, "Maximum participants");

        if (activity.getStatus() == null) {
            activity.setStatus(ActivityStatus.SCHEDULED);
        }
    }
}
