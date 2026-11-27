-- I2. Suất chiếu ngày 05/12/2026 còn từ 20 ghế (xem đề). Viết MỘT câu SELECT bên dưới, kết thúc bằng dấu ;

SELECT m.title, r.name AS room_name, st.start_time, st.available_seats
FROM showtimes st
JOIN movies m ON m.id = st.movie_id
JOIN rooms r ON r.id = st.room_id
WHERE st.start_time >= '2026-12-05 00:00:00'
  AND st.start_time < '2026-12-06 00:00:00'
  AND st.available_seats >= 20
ORDER BY st.start_time ASC, r.name ASC;
