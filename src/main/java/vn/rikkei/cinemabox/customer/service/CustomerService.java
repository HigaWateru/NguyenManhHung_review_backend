package vn.rikkei.cinemabox.customer.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.rikkei.cinemabox.common.BadRequestException;
import vn.rikkei.cinemabox.common.ConflictException;
import vn.rikkei.cinemabox.common.PageResponse;
import vn.rikkei.cinemabox.common.ResourceNotFoundException;
import vn.rikkei.cinemabox.customer.Customer;
import vn.rikkei.cinemabox.customer.CustomerRepository;
import vn.rikkei.cinemabox.customer.dto.CreateCustomerRequest;
import vn.rikkei.cinemabox.customer.dto.CustomerResponse;

@Service
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional
    public CustomerResponse create(CreateCustomerRequest req) {
        String email = req.email().trim();
        if (customerRepository.existsByEmail(email)) {
            throw new ConflictException("Email đã tồn tại: " + email);
        }

        Customer customer = new Customer();
        customer.setFullName(req.fullName().trim());
        customer.setEmail(email);
        customer.setPhone(req.phone());
        customer.setMemberLevel(req.memberLevel() == null ? "STANDARD" : req.memberLevel());

        Customer saved = customerRepository.save(customer);
        return CustomerResponse.from(saved);
    }

    public CustomerResponse getById(Long id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khách hàng id=" + id));
        return CustomerResponse.from(customer);
    }

    public PageResponse<CustomerResponse> list(int page, int size) {
        if (page < 0) {
            throw new BadRequestException("Tham số page phải >= 0");
        }
        if (size < 1 || size > 50) {
            throw new BadRequestException("Tham số size phải nằm trong khoảng 1 đến 50");
        }

        Page<Customer> result = customerRepository.findAll(
                PageRequest.of(page, size, Sort.by("id").ascending())
        );
        return PageResponse.from(result.map(CustomerResponse::from));
    }
}
