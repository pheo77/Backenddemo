package com.example.execrise2.entity;

public enum FeedbackStatus {
    PENDING,        // Mới gửi, đang chờ hệ thống/Admin phân loại (UC06)
    ASSIGNED,       // Đã phân công về UBND Phường hoặc Công an (UC42)
    IN_PROGRESS,    // Cán bộ đã tiếp nhận và đang xử lý (UC20, UC30)
    WAITING_INFO,   // Cán bộ yêu cầu người dân bổ sung thêm thông tin (UC22)
    RESOLVED,       // Đã xử lý xong, có kết quả (UC23, UC25)
    REJECTED        // Từ chối xử lý (do sai sự thật, spam, không thuộc thẩm quyền) (UC21)
}
