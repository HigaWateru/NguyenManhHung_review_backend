package vn.rikkei.cinemabox.showtime.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import vn.rikkei.cinemabox.showtime.Showtime;

public record ShowtimeResponse(
        Long id,
        String movieTitle,
        String roomName,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime startTime,
        Long price,
        Integer availableSeats) {

    public static ShowtimeResponse from(Showtime s) {
        return new ShowtimeResponse(
                s.getId(),
                s.getMovie().getTitle(),
                s.getRoom().getName(),
                s.getStartTime(),
                s.getPrice(),
                s.getAvailableSeats()
        );
    }
}
