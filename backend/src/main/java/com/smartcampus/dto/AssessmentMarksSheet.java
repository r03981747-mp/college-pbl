package com.smartcampus.dto;

import java.util.List;

import com.smartcampus.model.Assessment;
import com.smartcampus.model.StudentMark;

/** Assessment details plus one editable row per enrolled student. */
public record AssessmentMarksSheet(Assessment assessment, List<StudentMark> marks) {
}
