package vn.rikkei.cinemabox.showtime.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.rikkei.cinemabox.showtime.dto.ShowtimeResponse;
import vn.rikkei.cinemabox.showtime.service.ShowtimeService;

@RestController
@RequestMapping("/api/showtimes")
public class ShowtimeController {

    private final ShowtimeService showtimeService;

    public ShowtimeController(ShowtimeService showtimeService) {
        this.showtimeService = showtimeService;
    }

    @GetMapping("/{id}")
    public ShowtimeResponse getShowtimeById(@PathVariable Long id) {
        return showtimeService.getById(id);
    }
}
