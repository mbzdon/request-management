package pl.bzdon.requestmanagement.endpoint.controller;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import pl.bzdon.requestmanagement.domain.query.PageQuery;
import pl.bzdon.requestmanagement.domain.model.Request;
import pl.bzdon.requestmanagement.domain.query.RequestFilter;
import pl.bzdon.requestmanagement.domain.model.statemachine.RequestState;
import pl.bzdon.requestmanagement.domain.service.RequestService;
import pl.bzdon.requestmanagement.endpoint.pagination.PaginationProperties;
import pl.bzdon.requestmanagement.endpoint.config.OpenApiConfiguration;
import pl.bzdon.requestmanagement.endpoint.dto.CreateRequestDto;
import pl.bzdon.requestmanagement.endpoint.dto.EditBodyDto;
import pl.bzdon.requestmanagement.endpoint.dto.HistoryEntryDto;
import pl.bzdon.requestmanagement.endpoint.pagination.PageDto;
import pl.bzdon.requestmanagement.endpoint.dto.RequestDto;
import pl.bzdon.requestmanagement.endpoint.dto.RequestSummaryDto;
import pl.bzdon.requestmanagement.endpoint.dto.TransitionDto;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(value = "/api/requests", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Requests", description = "Create, edit, change state and browse requests")
@RequiredArgsConstructor
public class RequestController {

    private final RequestService requestService;
    private final PaginationProperties pagination;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ApiResponse(responseCode = "201", description = "Request created in state CREATED")
    @ApiResponse(responseCode = "400", ref = OpenApiConfiguration.BAD_REQUEST)
    ResponseEntity<RequestDto> create(@Valid @RequestBody CreateRequestDto dto) {
        Request created = requestService.create(dto.name(), dto.body());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(created.getId())
            .toUri();
        return ResponseEntity.created(location).body(RequestDto.from(created));
    }

    @PostMapping("/{id}/transitions")
    @ApiResponse(responseCode = "200", description = "Action performed, the request is in its new state")
    @ApiResponse(responseCode = "400", ref = OpenApiConfiguration.BAD_REQUEST)
    @ApiResponse(responseCode = "404", ref = OpenApiConfiguration.NOT_FOUND)
    @ApiResponse(responseCode = "409", ref = OpenApiConfiguration.CONFLICT)
    RequestDto changeState(@PathVariable UUID id, @Valid @RequestBody TransitionDto dto) {
        return RequestDto.from(requestService.changeState(id, dto.action(), dto.reason()));
    }

    @PatchMapping("/{id}")
    @ApiResponse(responseCode = "200", description = "Body edited")
    @ApiResponse(responseCode = "400", ref = OpenApiConfiguration.BAD_REQUEST)
    @ApiResponse(responseCode = "404", ref = OpenApiConfiguration.NOT_FOUND)
    @ApiResponse(responseCode = "409", ref = OpenApiConfiguration.CONFLICT)
    RequestDto editBody(@PathVariable UUID id, @Valid @RequestBody EditBodyDto dto) {
        return RequestDto.from(requestService.editBody(id, dto.body()));
    }

    @GetMapping("/{id}")
    @ApiResponse(responseCode = "200", description = "The request, including its body")
    @ApiResponse(responseCode = "404", ref = OpenApiConfiguration.NOT_FOUND)
    RequestDto get(@PathVariable UUID id) {
        return RequestDto.from(requestService.getById(id));
    }

    @GetMapping
    @ApiResponse(responseCode = "200", description = "A page of request summaries, newest first")
    @ApiResponse(responseCode = "400", ref = OpenApiConfiguration.BAD_REQUEST)
    PageDto<RequestSummaryDto> list(@RequestParam(required = false) String name,
                                    @RequestParam(required = false) RequestState state,
                                    @RequestParam(defaultValue = "0") @Min(0) int page,
                                    @RequestParam(required = false) @Min(1) Integer size) {
        PageQuery pageQuery = new PageQuery(page, pagination.resolve(size));
        return PageDto.from(requestService.findAll(new RequestFilter(name, state), pageQuery), RequestSummaryDto::from);
    }

    @GetMapping("/{id}/history")
    @ApiResponse(responseCode = "200", description = "State changes, oldest first")
    @ApiResponse(responseCode = "404", ref = OpenApiConfiguration.NOT_FOUND)
    List<HistoryEntryDto> history(@PathVariable UUID id) {
        return requestService.getHistory(id).stream().map(HistoryEntryDto::from).toList();
    }
}
