package vn.rikkei.cinemabox;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import vn.rikkei.cinemabox.booking.Booking;
import vn.rikkei.cinemabox.booking.BookingRepository;
import vn.rikkei.cinemabox.showtime.Showtime;
import vn.rikkei.cinemabox.showtime.ShowtimeRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Sql(scripts = {"/schema.sql", "/data.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class BookingApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ShowtimeRepository showtimeRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void datVeHopLe_vaGiamGiaGold() throws Exception {
        // Khách 2 (STANDARD), suất 9 (giá 80.000, còn 100 ghế), đặt 2 vé => 160.000
        mvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\": 2, \"showtimeId\": 9, \"quantity\": 2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value(2))
                .andExpect(jsonPath("$.showtimeId").value(9))
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.totalAmount").value(160000))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.paymentRef").isNotEmpty())
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        assertEquals(98, showtimeRepository.findById(9L).orElseThrow().getAvailableSeats());

        // Khách 1 (GOLD), suất 9 (giá 80.000), đặt 2 vé => 160.000 * 90% = 144.000
        mvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\": 1, \"showtimeId\": 9, \"quantity\": 2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalAmount").value(144000))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        assertEquals(96, showtimeRepository.findById(9L).orElseThrow().getAvailableSeats());
    }

    @Test
    void datVeDuLieuSai_vaKhongTonTai() throws Exception {
        mvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\": null, \"showtimeId\": 9, \"quantity\": 11}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.length()").value(2));

        mvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\": 9999, \"showtimeId\": 9, \"quantity\": 1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));

        mvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\": 2, \"showtimeId\": 9999, \"quantity\": 1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void datVeVuotSoGhe_vaDatDungBangSoGheConLai() throws Exception {
        // Suất 7 còn 8 ghế, giá 90.000
        long beforeBookings = bookingRepository.count();

        mvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\": 2, \"showtimeId\": 7, \"quantity\": 9}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("NOT_ENOUGH_SEATS"));

        assertEquals(8, showtimeRepository.findById(7L).orElseThrow().getAvailableSeats());
        assertEquals(beforeBookings, bookingRepository.count());

        // Đặt đúng 8 ghế (8 * 90.000 = 720.000 <= 1.000.000) phải thành công
        mvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\": 2, \"showtimeId\": 7, \"quantity\": 8}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalAmount").value(720000));

        assertEquals(0, showtimeRepository.findById(7L).orElseThrow().getAvailableSeats());
    }

    @Test
    void thanhToanBiTuChoi_tra402_vaHoanTacDuLieu() throws Exception {
        // Suất 11 giá 130.000, còn 25 ghế; khách 2 (STANDARD) đặt 8 vé => 1.040.000 > 1.000.000 => bị từ chối
        int seatsBefore = showtimeRepository.findById(11L).orElseThrow().getAvailableSeats();
        int confirmedBefore = bookingRepository.findByShowtimeIdAndStatus(11L, "CONFIRMED").size();

        mvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\": 2, \"showtimeId\": 11, \"quantity\": 8}"))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.status").value(402))
                .andExpect(jsonPath("$.error").value("PAYMENT_FAILED"));

        int seatsAfter = showtimeRepository.findById(11L).orElseThrow().getAvailableSeats();
        int confirmedAfter = bookingRepository.findByShowtimeIdAndStatus(11L, "CONFIRMED").size();

        assertEquals(seatsBefore, seatsAfter, "Số ghế phải được hoàn tác giữ nguyên khi thanh toán thất bại");
        assertEquals(confirmedBefore, confirmedAfter, "Không được còn vé CONFIRMED mới khi thanh toán thất bại");
    }

    @Test
    void huyVe_traLaiGhe_vaChanHuyLai() throws Exception {
        // Đặt 3 vé suất 9 (còn 100 -> 97)
        MvcResult createRes = mvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\": 2, \"showtimeId\": 9, \"quantity\": 3}"))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode body = objectMapper.readTree(createRes.getResponse().getContentAsString());
        long bookingId = body.get("id").asLong();
        assertEquals(97, showtimeRepository.findById(9L).orElseThrow().getAvailableSeats());

        // Huỷ vé thành công -> trả lại 3 ghế (97 -> 100)
        mvc.perform(post("/api/bookings/" + bookingId + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.quantity").value(3));

        assertEquals(100, showtimeRepository.findById(9L).orElseThrow().getAvailableSeats());

        // Huỷ lại lần 2 -> 409 INVALID_STATE
        mvc.perform(post("/api/bookings/" + bookingId + "/cancel"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVALID_STATE"));

        // Huỷ vé không tồn tại -> 404 NOT_FOUND
        mvc.perform(post("/api/bookings/9999/cancel"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void rangBuocDb_chanSoGheAm() {
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbcTemplate.update("UPDATE showtimes SET available_seats = -1 WHERE id = 1"));
    }

    @Test
    void datVeDongThoi_motSuatChieu_khongBanVuot_khongTuChoiNham() throws Exception {
        // Suất 7 có 8 ghế trống, giá 90.000
        long showtimeId = 7L;
        int initialSeats = showtimeRepository.findById(showtimeId).orElseThrow().getAvailableSeats();
        int initialConfirmedQty = bookingRepository.findByShowtimeIdAndStatus(showtimeId, "CONFIRMED")
                .stream().mapToInt(Booking::getQuantity).sum();

        int totalThreads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch ready = new CountDownLatch(totalThreads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < totalThreads; i++) {
            Callable<Integer> task = () -> {
                ready.countDown();
                start.await();
                MvcResult res = mvc.perform(post("/api/bookings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"customerId\": 2, \"showtimeId\": 7, \"quantity\": 1}"))
                        .andReturn();
                return res.getResponse().getStatus();
            };
            futures.add(pool.submit(task));
        }

        ready.await();
        start.countDown();

        int ok201 = 0;
        int conflict409 = 0;
        for (Future<Integer> f : futures) {
            int code = f.get();
            if (code == 201) {
                ok201++;
            } else if (code == 409) {
                conflict409++;
            } else {
                fail("Mã trạng thái không mong đợi: " + code);
            }
        }
        pool.shutdown();

        Showtime after = showtimeRepository.findById(showtimeId).orElseThrow();
        List<Booking> confirmedAfter = bookingRepository.findByShowtimeIdAndStatus(showtimeId, "CONFIRMED");
        int newConfirmedQty = confirmedAfter.stream().mapToInt(Booking::getQuantity).sum() - initialConfirmedQty;

        assertEquals(initialSeats, ok201, "Phải bán đúng bằng số ghế trống");
        assertEquals(totalThreads - initialSeats, conflict409, "Số request vượt ghế phải nhận 409");
        assertEquals(0, after.getAvailableSeats(), "Số ghế còn lại phải bằng 0");
        assertEquals(initialSeats, after.getAvailableSeats() + newConfirmedQty);
        assertTrue(confirmedAfter.stream().allMatch(b -> b.getPaymentRef() != null && !b.getPaymentRef().isBlank()));
    }

    @Test
    void datVeDongThoi_haiSuatChieu_vaLanThanhToanLoi() throws Exception {
        // Suất 11 (giá 130.000, 25 ghế): 6 request hợp lệ (qty=1) + 4 request thanh toán lỗi (qty=8 => 1.040.000 > 1tr)
        // Suất 7 (giá 90.000, 8 ghế): 12 request hợp lệ (qty=1) => đúng 8 thành công, 4 từ chối 409
        int initialSeats11 = showtimeRepository.findById(11L).orElseThrow().getAvailableSeats();
        int initialQty11 = bookingRepository.findByShowtimeIdAndStatus(11L, "CONFIRMED")
                .stream().mapToInt(Booking::getQuantity).sum();

        int initialSeats7 = showtimeRepository.findById(7L).orElseThrow().getAvailableSeats();
        int initialQty7 = bookingRepository.findByShowtimeIdAndStatus(7L, "CONFIRMED")
                .stream().mapToInt(Booking::getQuantity).sum();

        int totalThreads = 6 + 4 + 12;
        ExecutorService pool = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch ready = new CountDownLatch(totalThreads);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<Integer>> showtime11Valid = new ArrayList<>();
        List<Future<Integer>> showtime11Fail = new ArrayList<>();
        List<Future<Integer>> showtime7Valid = new ArrayList<>();

        for (int i = 0; i < 6; i++) {
            showtime11Valid.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                return mvc.perform(post("/api/bookings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"customerId\": 2, \"showtimeId\": 11, \"quantity\": 1}"))
                        .andReturn().getResponse().getStatus();
            }));
        }
        for (int i = 0; i < 4; i++) {
            showtime11Fail.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                return mvc.perform(post("/api/bookings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"customerId\": 2, \"showtimeId\": 11, \"quantity\": 8}"))
                        .andReturn().getResponse().getStatus();
            }));
        }
        for (int i = 0; i < 12; i++) {
            showtime7Valid.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                return mvc.perform(post("/api/bookings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"customerId\": 1, \"showtimeId\": 7, \"quantity\": 1}"))
                        .andReturn().getResponse().getStatus();
            }));
        }

        ready.await();
        start.countDown();

        for (Future<Integer> f : showtime11Valid) {
            assertEquals(201, f.get());
        }
        for (Future<Integer> f : showtime11Fail) {
            assertEquals(402, f.get());
        }
        int ok7 = 0;
        int conflict7 = 0;
        for (Future<Integer> f : showtime7Valid) {
            int code = f.get();
            if (code == 201) {
                ok7++;
            } else if (code == 409) {
                conflict7++;
            } else {
                fail("Mã không mong đợi ở suất 7: " + code);
            }
        }
        pool.shutdown();

        // Kiểm tra bất biến suất 11
        int finalSeats11 = showtimeRepository.findById(11L).orElseThrow().getAvailableSeats();
        List<Booking> confirmed11 = bookingRepository.findByShowtimeIdAndStatus(11L, "CONFIRMED");
        int newQty11 = confirmed11.stream().mapToInt(Booking::getQuantity).sum() - initialQty11;
        assertEquals(6, newQty11);
        assertEquals(initialSeats11, finalSeats11 + newQty11);
        assertTrue(confirmed11.stream().allMatch(b -> b.getPaymentRef() != null && !b.getPaymentRef().isBlank()));

        // Kiểm tra bất biến suất 7
        int finalSeats7 = showtimeRepository.findById(7L).orElseThrow().getAvailableSeats();
        List<Booking> confirmed7 = bookingRepository.findByShowtimeIdAndStatus(7L, "CONFIRMED");
        int newQty7 = confirmed7.stream().mapToInt(Booking::getQuantity).sum() - initialQty7;
        assertEquals(8, ok7);
        assertEquals(4, conflict7);
        assertEquals(0, finalSeats7);
        assertEquals(initialSeats7, finalSeats7 + newQty7);
        assertTrue(confirmed7.stream().allMatch(b -> b.getPaymentRef() != null && !b.getPaymentRef().isBlank()));
    }
}
