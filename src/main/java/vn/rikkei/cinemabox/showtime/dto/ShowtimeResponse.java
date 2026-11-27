package vn.rikkei.cinemabox.showtime.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import vn.rikkei.cinemabox.showtime.Showtime;

@NoArgsConstructor
@AllArgsConstructor
@Getter @Setter @Builder
public class ShowtimeResponse {
    private Long id;
    private String movieTitle;
    private String roomName;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private String startTime;

    private Long price;
    private Integer availableSeats;

    public static ShowtimeResponse fromEntity(Showtime showtime) {
        return ShowtimeResponse.builder()
                .id(showtime.getId())
                .movieTitle(showtime.getMovie().getTitle())
                .roomName(showtime.getRoom().getName())
                .startTime(showtime.getStartTime().toString())
                .price(showtime.getPrice())
                .availableSeats(showtime.getAvailableSeats())
                .build();
    }
}
