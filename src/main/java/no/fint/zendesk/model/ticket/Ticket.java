package no.fint.zendesk.model.ticket;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class Ticket {
    @NotNull
    private String subject;

    @NotNull
    private String type;
    @NotNull
    private long submitterId;

    @NotNull
    private String priority;

    @NotNull
    private List<String> tags;

    @NotNull
    private long requesterId;

    @NotNull
    private Comment comment;

    private String createdAt;
    private String description;
    private long id;
    private long assigneeId;
    private String url;
    private String status;
}