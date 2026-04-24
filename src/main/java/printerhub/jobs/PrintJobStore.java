package printerhub.jobs;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class PrintJobStore {

    private final ConcurrentMap<String, PrintJob> jobs = new ConcurrentHashMap<>();

    public PrintJob create(String name, PrintJobType type) {
        PrintJob job = PrintJob.create(name, type);
        jobs.put(job.getId(), job);
        return job;
    }

    public List<PrintJob> findAll() {
        return new ArrayList<>(jobs.values());
    }

    public Optional<PrintJob> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }

        return Optional.ofNullable(jobs.get(id.trim()));
    }

    public int size() {
        return jobs.size();
    }
}