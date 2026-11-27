package vn.rikkei.cinemabox;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Sql(scripts = {"/schema.sql", "/data.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class CinemaBoxApiAndSqlTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void apiShowtime_vaKhongTimThay() throws Exception {
        mvc.perform(get("/api/showtimes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.movieTitle").value("Biệt Đội Sấm Sét"))
                .andExpect(jsonPath("$.roomName").value("Phòng IMAX"))
                .andExpect(jsonPath("$.startTime").value("2026-12-04T19:00:00"))
                .andExpect(jsonPath("$.price").value(120000))
                .andExpect(jsonPath("$.availableSeats").value(35));

        mvc.perform(get("/api/showtimes/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void apiCustomer_dayDuNghiepVuVaLoi() throws Exception {
        // Tạo hợp lệ, mặc định STANDARD
        mvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\": \"Trần Thị Thu\", \"email\": \"thu.tran@example.com\", \"phone\": \"0912345678\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(9))
                .andExpect(jsonPath("$.fullName").value("Trần Thị Thu"))
                .andExpect(jsonPath("$.memberLevel").value("STANDARD"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        // Đọc lại khách vừa tạo & id không tồn tại
        mvc.perform(get("/api/customers/9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("thu.tran@example.com"));

        mvc.perform(get("/api/customers/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));

        // Trùng email -> 409 CONFLICT
        mvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\": \"Trùng\", \"email\": \"thu.tran@example.com\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));

        // Validation sai -> 400 VALIDATION_FAILED
        mvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\": \"   \", \"email\": \"khong-hop-le\", \"phone\": \"123\", \"memberLevel\": \"VIP\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.length()").value(4));

        // JSON hỏng -> 400 BAD_REQUEST
        mvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{json-hong"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));

        // Phân trang hợp lệ, mặc định, trang vượt quá, tham số sai
        mvc.perform(get("/api/customers?page=0&size=3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.totalElements").value(9))
                .andExpect(jsonPath("$.totalPages").value(3));

        mvc.perform(get("/api/customers?page=99&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(9));

        mvc.perform(get("/api/customers?page=-1&size=10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));

        mvc.perform(get("/api/customers?page=0&size=51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));

        mvc.perform(get("/api/customers?page=abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    void kiemThuTatCaSqlInternVaFresher() throws Exception {
        // I1
        String i1 = Files.readString(Path.of("sql/intern/i1_phim_hanh_dong.sql"));
        List<Map<String, Object>> r1 = jdbcTemplate.queryForList(i1);
        assertEquals(4, r1.size());
        assertEquals("Cuộc Đua Cuối Cùng", r1.get(0).get("title"));
        assertEquals("Mật Mã Biển Đông", r1.get(3).get("title"));

        // I2
        String i2 = Files.readString(Path.of("sql/intern/i2_suat_chieu_05_12.sql"));
        List<Map<String, Object>> r2 = jdbcTemplate.queryForList(i2);
        assertEquals(6, r2.size());
        assertTrue(r2.get(0).containsKey("room_name"));
        assertEquals("Phòng 2", r2.get(2).get("room_name"));
        assertEquals("Phòng IMAX", r2.get(3).get("room_name"));

        // I3
        String i3 = Files.readString(Path.of("sql/intern/i3_ve_cua_khach.sql"));
        List<Map<String, Object>> r3 = jdbcTemplate.queryForList(i3);
        assertEquals(3, r3.size());
        assertTrue(r3.get(0).containsKey("booking_id"));

        // F1: tạo bảng reviews và kiểm tra ràng buộc
        jdbcTemplate.execute("DROP TABLE IF EXISTS reviews");
        String f1 = Files.readString(Path.of("sql/fresher/f1_reviews.sql"));
        jdbcTemplate.execute(f1);

        jdbcTemplate.update("INSERT INTO reviews (customer_id, movie_id, rating, content) VALUES (1, 1, 5, 'Hay')");
        jdbcTemplate.update("INSERT INTO reviews (customer_id, movie_id, rating) VALUES (2, 1, 4)");
        assertNotNull(jdbcTemplate.queryForObject("SELECT created_at FROM reviews WHERE customer_id = 1 AND movie_id = 1", Object.class));

        // Chặn trùng (customer_id, movie_id)
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbcTemplate.update("INSERT INTO reviews (customer_id, movie_id, rating) VALUES (1, 1, 3)"));
        // Chặn rating ngoài 1..5
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbcTemplate.update("INSERT INTO reviews (customer_id, movie_id, rating) VALUES (3, 1, 6)"));
        // Chặn FK không tồn tại
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbcTemplate.update("INSERT INTO reviews (customer_id, movie_id, rating) VALUES (999, 1, 5)"));
        // Chặn created_at NULL
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbcTemplate.update("INSERT INTO reviews (customer_id, movie_id, rating, created_at) VALUES (3, 2, 5, NULL)"));

        // F2: đủ 10 phim, phim chưa bán vé có so_ve=0, doanh_thu=0
        String f2 = Files.readString(Path.of("sql/fresher/f2_doanh_thu_theo_phim.sql"));
        List<Map<String, Object>> rF2 = jdbcTemplate.queryForList(f2);
        assertEquals(10, rF2.size());
        Map<String, Object> lastMovie = rF2.get(9);
        assertEquals(0L, ((Number) lastMovie.get("so_ve")).longValue());
        assertEquals(0L, ((Number) lastMovie.get("doanh_thu")).longValue());

        // F3: khách tổng chi tiêu >= 500.000
        String f3 = Files.readString(Path.of("sql/fresher/f3_khach_chi_tieu_cao.sql"));
        List<Map<String, Object>> rF3 = jdbcTemplate.queryForList(f3);
        assertEquals(4, rF3.size());
        assertTrue(rF3.get(0).containsKey("full_name"));
        assertTrue(rF3.get(0).containsKey("so_lan_dat"));
        assertTrue(rF3.get(0).containsKey("tong_chi_tieu"));
    }
}
