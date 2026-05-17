package com.example.execrise2.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackRequest {
    private String title;
    private String description;
    private Double latitude;
    private Double longitude;
    private String addressDetails;
    private Long categoryId;
    private Long citizenId; // Tạm thời để lấy từ client, sau này sẽ lấy từ Token
}
