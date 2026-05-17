package com.example.execrise2.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "feedback_logs")
public class FeedbackLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feedback_id", nullable = false)
    private Feedback feedback;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "action_by_id", nullable = false)
    private User actionBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_status", length = 20)
    private FeedbackStatus oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", length = 20)
    private FeedbackStatus newStatus;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public FeedbackLog() {}

    public FeedbackLog(Feedback feedback, User actionBy, FeedbackStatus oldStatus, FeedbackStatus newStatus, String note) {
        this.feedback = feedback;
        this.actionBy = actionBy;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.note = note;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Feedback getFeedback() { return feedback; }
    public void setFeedback(Feedback feedback) { this.feedback = feedback; }
    public User getActionBy() { return actionBy; }
    public void setActionBy(User actionBy) { this.actionBy = actionBy; }
    public FeedbackStatus getOldStatus() { return oldStatus; }
    public void setOldStatus(FeedbackStatus oldStatus) { this.oldStatus = oldStatus; }
    public FeedbackStatus getNewStatus() { return newStatus; }
    public void setNewStatus(FeedbackStatus newStatus) { this.newStatus = newStatus; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
