package vn.rikkei.cinemabox.customer.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import vn.rikkei.cinemabox.customer.Customer;

public record CustomerResponse(
        Long id,
        String fullName,
        String email,
        String phone,
        String memberLevel,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime createdAt) {

    public static CustomerResponse from(Customer c) {
        return new CustomerResponse(
                c.getId(),
                c.getFullName(),
                c.getEmail(),
                c.getPhone(),
                c.getMemberLevel(),
                c.getCreatedAt()
        );
    }
}
