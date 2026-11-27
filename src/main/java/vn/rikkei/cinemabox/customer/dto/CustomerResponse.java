package vn.rikkei.cinemabox.customer.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

import lombok.*;
import vn.rikkei.cinemabox.customer.Customer;

@NoArgsConstructor
@AllArgsConstructor
@Getter @Setter @Builder

public class CustomerResponse {
    private Long id;
    private String fullName;
    private String email;
    private String phone;
    private String memberLevel;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

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