package com.smartcampus.dto;

import java.util.List;

/** Bulk marks entry payload for one assessment. */
public record MarksSaveRequest(List<MarkEntry> marks) {
}
