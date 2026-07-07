package dev.rafex.insightbloom.users.domain.ports;

import dev.rafex.insightbloom.users.domain.model.Timezone;

import java.util.List;
import java.util.Optional;

public interface TimezoneRepository {
    List<Timezone> findAll();

    Timezone findDefault();

    Optional<Timezone> findById(int id);
}
