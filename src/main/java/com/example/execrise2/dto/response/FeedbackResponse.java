package com.example.execrise2.dto.response;

import com.example.execrise2.entity.FeedbackStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackResponse {
    private Long id;
    private String trackingCode;
    private String title;
    private String description;
    private Double latitude;
    private Double longitude;
    private String addressDetails;
    private FeedbackStatus status;
    private String categoryName;
    private String citizenName;
    private String assigneeName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
