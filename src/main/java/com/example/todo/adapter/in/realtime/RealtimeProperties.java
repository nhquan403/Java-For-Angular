package com.example.todo.adapter.in.realtime;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Cấu hình thời gian thực, đọc từ app.realtime.* (có giá trị mặc định nên không bắt buộc khai báo).
 *
 * @param maxConnectionsPerUser số kết nối (SSE + WebSocket) tối đa của một người, chống một người chiếm hết tài nguyên
 * @param queueCapacity         số sự kiện chờ gửi tối đa cho mỗi client. Đầy nghĩa là client quá chậm, ngắt nó
 * @param heartbeatInterval     khoảng im lặng tối đa trước khi gửi tín hiệu "còn sống" (giữ kết nối qua proxy, phát hiện kết nối chết)
 * @param maxConnectionAge      tuổi thọ tối đa của một kết nối. Hết thì server đóng, client kết nối lại bằng ticket mới.
 *                              Đặt bằng hạn access token để quyền không "sống" lâu hơn token
 * @param ticketTtl             ticket dùng một lần để mở SSE/WebSocket sống bao lâu
 */
@ConfigurationProperties(prefix = "app.realtime")
public record RealtimeProperties(
        @DefaultValue("5") int maxConnectionsPerUser,
        @DefaultValue("256") int queueCapacity,
        @DefaultValue("25s") Duration heartbeatInterval,
        @DefaultValue("15m") Duration maxConnectionAge,
        @DefaultValue("30s") Duration ticketTtl) {
}
