package com.example.execrise2.service;

import com.example.execrise2.dto.request.FeedbackRequest;
import com.example.execrise2.dto.response.FeedbackResponse;
import com.example.execrise2.entity.Category;
import com.example.execrise2.entity.Feedback;
import com.example.execrise2.entity.FeedbackStatus;
import com.example.execrise2.entity.User;
import com.example.execrise2.repository.CategoryRepository;
import com.example.execrise2.repository.FeedbackRepository;
import com.example.execrise2.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    @Transactional
    public FeedbackResponse createFeedback(FeedbackRequest request) {
        // 1. Validate Category & User
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new RuntimeException("Category không tồn tại"));

        User citizen = userRepository.findById(request.getCitizenId())
                .orElseThrow(() -> new RuntimeException("User không tồn tại"));

        // 2. Map Request -> Entity
        Feedback feedback = new Feedback();
        feedback.setTrackingCode("FB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        feedback.setTitle(request.getTitle());
        feedback.setDescription(request.getDescription());
        feedback.setLatitude(request.getLatitude());
        feedback.setLongitude(request.getLongitude());
        feedback.setAddressDetails(request.getAddressDetails());
        feedback.setStatus(FeedbackStatus.PENDING);
        feedback.setCategory(category);
        feedback.setCitizen(citizen);
        feedback.setCreatedAt(LocalDateTime.now());
        feedback.setUpdatedAt(LocalDateTime.now());

        // 3. Save
        feedbackRepository.save(feedback);

        // 4. Return DTO
        return mapToDTO(feedback);
    }

    @Transactional(readOnly = true)
    public List<FeedbackResponse> getAllFeedbacks() {
        return feedbackRepository.findAll().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    private FeedbackResponse mapToDTO(Feedback feedback) {
        return FeedbackResponse.builder()
                .id(feedback.getId())
                .trackingCode(feedback.getTrackingCode())
                .title(feedback.getTitle())
                .description(feedback.getDescription())
                .latitude(feedback.getLatitude())
                .longitude(feedback.getLongitude())
                .addressDetails(feedback.getAddressDetails())
                .status(feedback.getStatus())
                .categoryName(feedback.getCategory() != null ? feedback.getCategory().getName() : null)
                .citizenName(feedback.getCitizen() != null ? feedback.getCitizen().getFullName() : null)
                .assigneeName(feedback.getAssignee() != null ? feedback.getAssignee().getFullName() : null)
                .createdAt(feedback.getCreatedAt())
                .updatedAt(feedback.getUpdatedAt())
                .build();
    }
}
