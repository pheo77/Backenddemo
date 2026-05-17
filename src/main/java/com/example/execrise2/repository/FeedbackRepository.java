package com.example.execrise2.repository;

import com.example.execrise2.entity.Feedback;
import com.example.execrise2.entity.FeedbackStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    Optional<Feedback> findByTrackingCode(String trackingCode);
    List<Feedback> findByStatus(FeedbackStatus status);
}
