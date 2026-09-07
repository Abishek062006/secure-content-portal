package com.secureportal.stream;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Shared by every delivery endpoint: verify the ticket, and that it's for this endpoint's purpose. */
@Component
public class TicketGuard {

    private final StreamTicketService ticketService;

    public TicketGuard(StreamTicketService ticketService) {
        this.ticketService = ticketService;
    }

    public StreamTicket verify(String ticket, HttpServletRequest request, StreamTicket.Purpose expectedPurpose) {
        StreamTicket streamTicket;
        try {
            streamTicket = ticketService.verify(ticket, request);
        } catch (TicketException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        }
        if (streamTicket.purpose() != expectedPurpose) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Wrong ticket purpose");
        }
        return streamTicket;
    }
}
