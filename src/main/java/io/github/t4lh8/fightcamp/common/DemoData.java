package io.github.t4lh8.fightcamp.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import io.github.t4lh8.fightcamp.auth.Role;
import io.github.t4lh8.fightcamp.auth.UserRepository;
import io.github.t4lh8.fightcamp.auth.WeightClass;
import io.github.t4lh8.fightcamp.coach.CoachRepository;
import io.github.t4lh8.fightcamp.training.Discipline;
import io.github.t4lh8.fightcamp.training.Fight;
import io.github.t4lh8.fightcamp.training.FightRepository;
import io.github.t4lh8.fightcamp.training.FightRequest;
import io.github.t4lh8.fightcamp.training.SessionRequest;
import io.github.t4lh8.fightcamp.training.TrainingRepository;
import io.github.t4lh8.fightcamp.training.WeighInRepository;

/**
 * Fake demo data, only with the "demo" profile. All names, clubs and events are made up.
 * Ten weeks of a fight camp for a bantamweight, with a load spike in the last week.
 */
@Component
@Profile("demo")
public class DemoData implements ApplicationRunner {

    public static final String ATHLETE = "ingrid.solberg";
    public static final String COACH = "coach.nilsen";
    public static final String PASSWORD = "Demo-Bantam-Oslo-26";

    private static final Logger log = LoggerFactory.getLogger(DemoData.class);

    private final UserRepository users;
    private final TrainingRepository sessions;
    private final FightRepository fights;
    private final WeighInRepository weighIns;
    private final CoachRepository access;
    private final PasswordEncoder encoder;
    private final Clock clock;

    public DemoData(UserRepository users, TrainingRepository sessions, FightRepository fights,
                    WeighInRepository weighIns, CoachRepository access, PasswordEncoder encoder, Clock clock) {
        this.users = users;
        this.sessions = sessions;
        this.fights = fights;
        this.weighIns = weighIns;
        this.access = access;
        this.encoder = encoder;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.findByUsername(ATHLETE).isPresent()) {
            return;
        }
        String hash = encoder.encode(PASSWORD);
        long athlete = users.insert(ATHLETE, hash, Role.ATHLETE, WeightClass.BANTAMWEIGHT);
        long coach = users.insert(COACH, hash, Role.COACH, null);
        access.grant(athlete, coach);

        LocalDate today = LocalDate.now(clock);
        Random rnd = new Random(42);

        // Weekly plan: Mon striking, Tue wrestling, Wed BJJ, Thu strength, Fri sparring, Sat conditioning.
        Discipline[] plan = {Discipline.STRIKING, Discipline.WRESTLING, Discipline.BJJ,
                Discipline.STRENGTH, Discipline.SPARRING, Discipline.CONDITIONING};
        for (int day = 69; day >= 0; day--) {
            LocalDate date = today.minusDays(day);
            int dow = date.getDayOfWeek().getValue();
            if (dow == 7) {
                continue;
            }
            boolean lastWeek = day < 7;
            if (!lastWeek && rnd.nextInt(10) == 0) {
                continue; // a missed session now and then
            }
            Discipline d = plan[dow - 1];
            int minutes = 60 + rnd.nextInt(4) * 15 + (lastWeek ? 30 : 0);
            int rpe = Math.min(10, 5 + rnd.nextInt(3) + (d == Discipline.SPARRING ? 2 : 0) + (lastWeek ? 1 : 0));
            Integer rounds = d == Discipline.SPARRING ? 5 + rnd.nextInt(4) : null;
            sessions.insert(athlete, new SessionRequest(date, d, minutes, rpe, rounds, note(d, rnd)));
        }

        fights.insert(athlete, new FightRequest(today.minusMonths(20), "Maja Hansen", "Oslo Cage Night 4",
                Fight.Result.WIN, Fight.Method.DECISION, 3));
        fights.insert(athlete, new FightRequest(today.minusMonths(15), "Sofie Berg", "Bergen Fight Series 7",
                Fight.Result.LOSS, Fight.Method.SUBMISSION, 2));
        fights.insert(athlete, new FightRequest(today.minusMonths(10), "Nora Lie", "Trondheim Open MMA",
                Fight.Result.WIN, Fight.Method.SUBMISSION, 1));
        fights.insert(athlete, new FightRequest(today.minusMonths(6), "Emma Dahl", "Oslo Cage Night 9",
                Fight.Result.WIN, Fight.Method.KO_TKO, 2));
        fights.insert(athlete, new FightRequest(today.minusMonths(2), "Thea Strand", "Stavanger Fight Night 2",
                Fight.Result.WIN, Fight.Method.DECISION, 3));

        // Walking weight around 66 kg, slowly coming down towards 61.2.
        BigDecimal kg = new BigDecimal("67.4");
        for (int day = 63; day >= 0; day -= 3) {
            weighIns.upsert(athlete, today.minusDays(day), kg.setScale(1, RoundingMode.HALF_UP));
            kg = kg.subtract(BigDecimal.valueOf(0.12 + rnd.nextDouble() * 0.1));
        }
        log.info("Demo data loaded: log in as {} or {}", ATHLETE, COACH);
    }

    private static String note(Discipline d, Random rnd) {
        String[][] notes = {
                {"Jab-cross-low kick drills", "Pad work, body shots", "Counter striking off the cage"},
                {"Single legs and cage wrestling", "Chain wrestling", "Takedown defence vs bigger partner"},
                {"Guard retention", "Back takes from turtle", "Arm triangle setups"},
                {"Hard rounds, 5 min", "Mixed sparring, felt sharp", "Tired in the last rounds"},
                {"Deadlift and pull-ups", "Explosive lifts", "Neck and core"},
                {"Assault bike intervals", "Hill sprints", "Rounds on the bag, 30/30"}};
        String[] options = notes[d.ordinal()];
        return options[rnd.nextInt(options.length)];
    }
}
