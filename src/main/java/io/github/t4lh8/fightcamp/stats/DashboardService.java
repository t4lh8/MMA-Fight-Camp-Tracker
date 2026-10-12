package io.github.t4lh8.fightcamp.stats;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import io.github.t4lh8.fightcamp.auth.AppUser;
import io.github.t4lh8.fightcamp.auth.WeightClass;
import io.github.t4lh8.fightcamp.training.Fight;
import io.github.t4lh8.fightcamp.training.FightRepository;
import io.github.t4lh8.fightcamp.training.TrainingRepository;
import io.github.t4lh8.fightcamp.training.TrainingSession;

@Service
public class DashboardService {

    static final int WEEKS = 8;

    public record FightRecord(int wins, int losses, int draws, int noContests, int koWins, int subWins,
                              int decisionWins, BigDecimal finishRate, String streak) {
    }

    public record Dashboard(String athlete, WeightClass weightClass, LocalDate today, FightRecord record,
                            Workload workload, List<StatsRepository.WeekRow> weeks,
                            List<StatsRepository.DisciplineRow> disciplines, WeightCut weightCut,
                            List<TrainingSession> recentSessions, List<Fight> fights) {
    }

    private final StatsRepository stats;
    private final TrainingRepository sessions;
    private final FightRepository fights;
    private final Clock clock;

    public DashboardService(StatsRepository stats, TrainingRepository sessions, FightRepository fights, Clock clock) {
        this.stats = stats;
        this.sessions = sessions;
        this.fights = fights;
        this.clock = clock;
    }

    public Dashboard build(AppUser athlete) {
        long id = athlete.id();
        LocalDate today = LocalDate.now(clock);

        long[] windows = stats.loadWindows(id, today);
        Workload workload = Workload.of(windows[0], windows[1]);

        WeightCut cut = null;
        if (athlete.weightClass() != null) {
            cut = stats.latestWeight(id)
                    .map(w -> WeightCut.of(athlete.weightClass(), w.kg(), w.date()))
                    .orElse(null);
        }

        return new Dashboard(athlete.username(), athlete.weightClass(), today, record(id), workload,
                weeks(id, today), stats.disciplines(id, today.minusDays(27)), cut,
                sessions.list(id, 8), fights.list(id));
    }

    /** The last {@link #WEEKS} weeks, Monday based, with empty weeks filled in as zero. */
    List<StatsRepository.WeekRow> weeks(long ownerId, LocalDate today) {
        LocalDate thisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate first = thisWeek.minusWeeks(WEEKS - 1);
        Map<LocalDate, StatsRepository.WeekRow> found = stats.weeklyLoad(ownerId, first).stream()
                .collect(Collectors.toMap(StatsRepository.WeekRow::weekStart, Function.identity()));
        List<StatsRepository.WeekRow> weeks = new ArrayList<>(WEEKS);
        for (int i = 0; i < WEEKS; i++) {
            LocalDate week = first.plusWeeks(i);
            weeks.add(found.getOrDefault(week, new StatsRepository.WeekRow(week, 0, 0, 0)));
        }
        return weeks;
    }

    FightRecord record(long ownerId) {
        StatsRepository.RecordRow r = stats.record(ownerId);
        BigDecimal finishRate = r.wins() == 0 ? null
                : BigDecimal.valueOf((r.koWins() + r.subWins()) * 100L)
                        .divide(BigDecimal.valueOf(r.wins()), 0, RoundingMode.HALF_UP);
        return new FightRecord(r.wins(), r.losses(), r.draws(), r.noContests(), r.koWins(), r.subWins(),
                r.decisionWins(), finishRate, streak(stats.resultsNewestFirst(ownerId)));
    }

    /** "W3" for three wins in a row, "L1" after a loss, null without fights. */
    static String streak(List<String> newestFirst) {
        if (newestFirst.isEmpty()) {
            return null;
        }
        String latest = newestFirst.get(0);
        int count = 0;
        for (String result : newestFirst) {
            if (!result.equals(latest)) {
                break;
            }
            count++;
        }
        return latest.charAt(0) + String.valueOf(count);
    }
}
