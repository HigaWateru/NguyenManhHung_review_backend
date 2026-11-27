-- I3. Vé đã xác nhận của khách an.nguyen@example.com (xem đề). Viết MỘT câu SELECT bên dưới, kết thúc bằng dấu ;

SELECT b.id AS booking_id, m.title, st.start_time, b.quantity, b.total_amount
FROM bookings b
JOIN customers c ON c.id = b.customer_id
JOIN showtimes st ON st.id = b.showtime_id
JOIN movies m ON m.id = st.movie_id
WHERE c.email = 'an.nguyen@example.com'
  AND b.status = 'CONFIRMED'
ORDER BY st.start_time ASC;
