package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.WorkspaceZipJob;
import dev.rafex.insightbloom.users.domain.ports.WorkspaceZipJobRepository;

/** Consultado por el botón de IdePage.vue mientras espera que el job pase a READY/FAILED. */
public class GetWorkspaceZipJobStatusUseCase {
    private final WorkspaceZipJobRepository jobRepository;
    private final String downloadBaseUrl;

    public GetWorkspaceZipJobStatusUseCase(final WorkspaceZipJobRepository jobRepository,
                                            final String downloadBaseUrl) {
        this.jobRepository = jobRepository;
        this.downloadBaseUrl = downloadBaseUrl;
    }

    public record Result(String status, String downloadUrl, String errorMessage) {}

    public Result execute(final String jobUuid, final String userUuid) {
        final WorkspaceZipJob job = jobRepository.findByUuid(jobUuid)
                .orElseThrow(() -> new IllegalArgumentException("job_not_found"));
        if (!job.userUuid().equals(userUuid)) {
            throw new IllegalArgumentException("job_not_found");
        }
        if (WorkspaceZipJob.STATUS_READY.equals(job.status()) && !job.isExpired()) {
            final String downloadUrl = downloadBaseUrl + "/workspaces/" + job.uuid()
                    + "/download?token=" + job.downloadToken();
            return new Result(job.status(), downloadUrl, null);
        }
        if (WorkspaceZipJob.STATUS_READY.equals(job.status())) {
            return new Result("EXPIRED", null, null);
        }
        return new Result(job.status(), null, job.errorMessage());
    }
}
