package vn.rikkei.cinemabox.showtime.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.rikkei.cinemabox.common.ResourceNotFoundException;
import vn.rikkei.cinemabox.showtime.Showtime;
import vn.rikkei.cinemabox.showtime.ShowtimeRepository;
import vn.rikkei.cinemabox.showtime.dto.ShowtimeResponse;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShowtimeService {
    private final ShowtimeRepository showtimeRepository;

    public ShowtimeResponse getById(Long id) {
        Showtime showtime = showtimeRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy suất chiếu id=" + id));
        return ShowtimeResponse.fromEntity(showtime);
    }
}
