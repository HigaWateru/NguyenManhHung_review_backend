package vn.rikkei.cinemabox.booking;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.rikkei.cinemabox.common.InvalidStateException;
import vn.rikkei.cinemabox.common.NotEnoughSeatsException;
import vn.rikkei.cinemabox.common.ResourceNotFoundException;
import vn.rikkei.cinemabox.customer.Customer;
import vn.rikkei.cinemabox.customer.CustomerRepository;
import vn.rikkei.cinemabox.payment.PaymentGateway;
import vn.rikkei.cinemabox.showtime.Showtime;
import vn.rikkei.cinemabox.showtime.ShowtimeRepository;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final CustomerRepository customerRepository;
    private final ShowtimeRepository showtimeRepository;
    private final PaymentGateway paymentGateway;

    public BookingService(
            BookingRepository bookingRepository,
            CustomerRepository customerRepository,
            ShowtimeRepository showtimeRepository,
            PaymentGateway paymentGateway) {
        this.bookingRepository = bookingRepository;
        this.customerRepository = customerRepository;
        this.showtimeRepository = showtimeRepository;
        this.paymentGateway = paymentGateway;
    }

    @Transactional
    public BookingResponse create(CreateBookingRequest req) {
        Customer customer = customerRepository.findById(req.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khách hàng id=" + req.customerId()));

        // Khoá bi quan (SELECT ... FOR UPDATE) trên dòng suất chiếu để chống bán vượt ghế
        // và tránh từ chối nhầm khi có request đồng thời bị lỗi thanh toán hoàn tác.
        Showtime showtime = showtimeRepository.findByIdForUpdate(req.showtimeId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy suất chiếu id=" + req.showtimeId()));

        int quantity = req.quantity();
        if (showtime.getAvailableSeats() < quantity) {
            throw new NotEnoughSeatsException(
                    "Suất chiếu id=" + showtime.getId() + " chỉ còn " + showtime.getAvailableSeats() + " ghế");
        }

        long subtotal = showtime.getPrice() * (long) quantity;
        long totalAmount = "GOLD".equalsIgnoreCase(customer.getMemberLevel())
                ? (subtotal * 9L) / 10L
                : subtotal;

        showtime.setAvailableSeats(showtime.getAvailableSeats() - quantity);
        showtimeRepository.saveAndFlush(showtime);

        // Nếu cổng thanh toán ném PaymentFailedException (RuntimeException),
        // @Transactional sẽ tự động rollback việc trừ ghế và không tạo bản ghi Booking.
        String paymentRef = paymentGateway.charge(customer.getId(), totalAmount);

        Booking booking = new Booking();
        booking.setCustomer(customer);
        booking.setShowtime(showtime);
        booking.setQuantity(quantity);
        booking.setTotalAmount(totalAmount);
        booking.setStatus("CONFIRMED");
        booking.setPaymentRef(paymentRef);

        Booking saved = bookingRepository.saveAndFlush(booking);
        return BookingResponse.from(saved);
    }

    @Transactional
    public BookingResponse cancel(Long bookingId) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vé id=" + bookingId));

        if (!"CONFIRMED".equals(booking.getStatus())) {
            throw new InvalidStateException("Vé id=" + bookingId + " không ở trạng thái CONFIRMED");
        }

        Showtime showtime = showtimeRepository.findByIdForUpdate(booking.getShowtime().getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy suất chiếu id=" + booking.getShowtime().getId()));

        booking.setStatus("CANCELLED");
        showtime.setAvailableSeats(showtime.getAvailableSeats() + booking.getQuantity());

        showtimeRepository.saveAndFlush(showtime);
        Booking saved = bookingRepository.saveAndFlush(booking);
        return BookingResponse.from(saved);
    }
}
