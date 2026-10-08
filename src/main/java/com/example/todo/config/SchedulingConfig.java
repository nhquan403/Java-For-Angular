package com.example.todo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Bật @Scheduled (dùng cho việc dọn refresh token hết hạn). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
