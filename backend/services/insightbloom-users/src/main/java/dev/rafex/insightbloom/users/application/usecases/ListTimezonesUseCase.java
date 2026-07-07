package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Timezone;
import dev.rafex.insightbloom.users.domain.ports.TimezoneRepository;

import java.util.List;

public class ListTimezonesUseCase {
    private final TimezoneRepository timezoneRepository;

    public ListTimezonesUseCase(final TimezoneRepository timezoneRepository) {
        this.timezoneRepository = timezoneRepository;
    }

    public List<Timezone> execute() {
        return timezoneRepository.findAll();
    }
}
