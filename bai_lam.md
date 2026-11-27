# Bài làm CinemaBox – Database + Backend

Họ tên: Nguyễn Mạnh Hùng
Mã sinh viên: B24DTCN422
Lớp: K24CNTT1

## Pha 1 (không dùng AI)

Tự đánh dấu phần đã xong trước mốc 1 (chỉ để GV tham khảo, không thay cho kết quả chấm):

- [x] I1  - [x] I2  - [x] I3  - [x] Endpoint GET /api/showtimes/{id}
- [x] F1  - [x] F2  - [x] F3  - [x] API khách hàng (tạo, xem, danh sách, validate, lỗi)

Ghi chú (nếu có): Đã khôi phục cấu hình H2 gốc và `pom.xml` gốc (không dùng Lombok), kiểm thử chạy xanh toàn bộ bằng `mvn test`.

## Pha 2 (được dùng AI)

### Cách bạn chống bán vượt số ghế khi nhiều người đặt cùng lúc

- **Cách chọn:** Sử dụng **khoá bi quan (Pessimistic Write Lock)** ở mức dòng trên bảng `showtimes` thông qua `@Lock(LockModeType.PESSIMISTIC_WRITE)` (`SELECT ... FOR UPDATE`) trong `ShowtimeRepository.findByIdForUpdate(Long id)` bên trong giao dịch `@Transactional` của `BookingService.create(...)`, kết hợp ràng buộc ở mức cơ sở dữ liệu `CHECK (available_seats >= 0)` trong `schema.sql`.
- **Vì sao chọn cách này:** Khi nhiều luồng cùng đặt vé cho một suất chiếu, các giao dịch trên cùng một dòng `showtimes` sẽ được xếp hàng tuần tự: chỉ giao dịch đang giữ khoá mới đọc và cập nhật `available_seats`, nên không thể xảy ra race condition dẫn đến bán vượt ghế. Đồng thời, nếu một giao dịch bị lỗi thanh toán và rollback trước khi nhả khoá, giao dịch hợp lệ kế tiếp sẽ đọc đúng số ghế chưa bị trừ nên **không bị từ chối nhầm `409 NOT_ENOUGH_SEATS`**. Với hai suất chiếu khác nhau, khoá ở mức dòng cho phép hai suất chiếu xử lý song song không chặn nhau. Đối với thao tác huỷ vé (`POST /api/bookings/{id}/cancel`), khoá bi quan trên `Booking` rồi `Showtime` giúp tránh hoàn ghế trùng khi có nhiều yêu cầu huỷ đồng thời.
- **Cách kiểm chứng:** Viết test đa luồng JUnit 5 trong `BookingApiTest` sử dụng `ExecutorService` + `CountDownLatch` bắn đồng thời 20 luồng vào cùng 1 suất chiếu (8 ghế trống) và 22 luồng đồng thời vào 2 suất chiếu có lẫn request thanh toán vượt hạn mức 1.000.000đ; kiểm chứng số lượng `201`/`409`/`402`, `available_seats >= 0`, và bất biến `ghế ban đầu = ghế còn lại + tổng vé CONFIRMED mới`.

### Cách bạn hoàn tác khi thanh toán lỗi

- Phương thức `BookingService.create(...)` được đánh dấu `@Transactional`.
- Sau khi kiểm tra đủ ghế và trừ `available_seats` (gọi `saveAndFlush`), hệ thống gọi `PaymentGateway.charge(customerId, totalAmount)`.
- Vì `PaymentFailedException` kế thừa `RuntimeException`, khi cổng thanh toán từ chối, Spring tự động **rollback toàn bộ giao dịch**: câu lệnh trừ `available_seats` trên bảng `showtimes` được hoàn tác về giá trị ban đầu và không có bản ghi `Booking` nào được lưu vào CSDL.
- `GlobalExceptionHandler` bắt `PaymentFailedException` và trả về HTTP `402` với mã lỗi `"PAYMENT_FAILED"` theo đúng định dạng `ApiError`.

### Test bạn đã viết (tên lớp, tên test, mỗi test kiểm tra điều gì)

1. **`BookingApiTest`** (`src/test/java/vn/rikkei/cinemabox/BookingApiTest.java`):
   - `datVeHopLe_vaGiamGiaGold`: Kiểm tra đặt vé hợp lệ cho khách `STANDARD` (nguyên giá) và `GOLD` (giảm 10% làm tròn xuống), trừ đúng `available_seats`, trả `201` và lưu `paymentRef`.
   - `datVeDuLieuSai_vaKhongTonTai`: Kiểm tra dữ liệu đặt vé sai (`customerId` null, `quantity = 11`) trả `400 VALIDATION_FAILED` kèm `details`; khách hàng hoặc suất chiếu không tồn tại trả `404 NOT_FOUND`.
   - `datVeVuotSoGhe_vaDatDungBangSoGheConLai`: Kiểm tra đặt vượt ghế trả `409 NOT_ENOUGH_SEATS` và không đổi dữ liệu; đặt đúng bằng số ghế còn lại thành công (`available_seats = 0`).
   - `thanhToanBiTuChoi_tra402_vaHoanTacDuLieu`: Kiểm tra khi tổng tiền > 1.000.000đ, `FakePaymentGateway` ném `PaymentFailedException` → trả `402 PAYMENT_FAILED`, `available_seats` giữ nguyên và không phát sinh vé `CONFIRMED`.
   - `huyVe_traLaiGhe_vaChanHuyLai`: Kiểm tra `POST /api/bookings/{id}/cancel` chuyển vé sang `CANCELLED` và trả lại đúng số ghế; huỷ lần 2 trả `409 INVALID_STATE`; vé không tồn tại trả `404 NOT_FOUND`.
   - `rangBuocDb_chanSoGheAm`: Kiểm tra CSDL tự chặn `UPDATE showtimes SET available_seats = -1` bằng `DataIntegrityViolationException` nhờ ràng buộc `CHECK (available_seats >= 0)`.
   - `datVeDongThoi_motSuatChieu_khongBanVuot_khongTuChoiNham`: 20 luồng đặt đồng thời vào suất còn 8 ghế → đúng 8 vé `201`, 12 request `409`, `available_seats = 0`, tất cả vé `CONFIRMED` đều có `paymentRef`.
   - `datVeDongThoi_haiSuatChieu_vaLanThanhToanLoi`: 22 luồng đặt đồng thời vào 2 suất chiếu (có lẫn 4 request thanh toán lỗi > 1.000.000đ) → mã trả về (`201`, `402`, `409`) và bất biến dữ liệu ở cả 2 suất đều đúng.

2. **`CinemaBoxApiAndSqlTest`** (`src/test/java/vn/rikkei/cinemabox/CinemaBoxApiAndSqlTest.java`):
   - `apiShowtime_vaKhongTimThay`: Kiểm tra `GET /api/showtimes/{id}` đủ 6 trường, đúng định dạng `startTime` và `404 NOT_FOUND` khi không tồn tại.
   - `apiCustomer_dayDuNghiepVuVaLoi`: Kiểm tra đầy đủ 3 endpoint của API khách hàng (tạo mặc định `STANDARD`, xem chi tiết, phân trang, validation `400 VALIDATION_FAILED`, trùng email `409 CONFLICT`, JSON/tham số sai `400 BAD_REQUEST`).
   - `kiemThuTatCaSqlInternVaFresher`: Chạy trực tiếp 6 tệp SQL (`i1`, `i2`, `i3`, `f1`, `f2`, `f3`) trên H2 để kiểm tra kết quả, tên cột, thứ tự sắp xếp và các ràng buộc bảng `reviews`.

### Nhật ký dùng AI

| # | Bạn hỏi AI điều gì | AI gợi ý gì | Bạn giữ / sửa / bỏ, vì sao |
|---|---|---|---|
| 1 | Cách chấm, rà soát lỗi phần 4.1, 4.2 và sửa lỗi `LazyInitializationException` ở `ShowtimeService` | Chỉ ra lỗi thiếu `@Transactional(readOnly = true)` ở service, lỗi định dạng `startTime` do dùng `LocalDateTime.toString()`, và các lỗi cú pháp/tên cột trong SQL I1–I3, F1–F3 | **Giữ và áp dụng sửa toàn bộ**, đồng thời viết test `CinemaBoxApiAndSqlTest` chạy trực tiếp cả 6 file SQL và API trên H2 để kiểm chứng. |
| 2 | Cách xây dựng API khách hàng (4.2.4) và xử lý lỗi tập trung không dùng Lombok | Gợi ý chuyển DTO sang Java `record`, bổ sung `ConflictException`, `BadRequestException` và các `@ExceptionHandler` trong `GlobalExceptionHandler`; khôi phục `pom.xml` và `application.properties` gốc | **Giữ và sửa lại `CustomerService`** (sửa lỗi `req.getPhone().trim()` có thể gây `NullPointerException` khi `phone` null), kiểm chứng bằng `mvn test`. |
| 3 | Thiết kế API đặt vé an toàn đồng thời (4.3) và hoàn tác khi thanh toán lỗi | So sánh giữa trừ ghế trước ngoài transaction (dễ gây từ chối nhầm `409` khi có request thanh toán lỗi đồng thời) và khoá bi quan `PESSIMISTIC_WRITE` trong `@Transactional` kèm `CHECK (available_seats >= 0)` | **Giữ phương án khoá bi quan + `CHECK` DB** và bổ sung API huỷ vé `POST /api/bookings/{id}/cancel`; đã kiểm chứng bằng 8 test trong `BookingApiTest` (kể cả test 20–22 luồng đồng thời lẫn thanh toán lỗi), chạy `mvn test` xanh 13/13 test. |
