package com.spin.FamilySpin.repository;

import com.spin.FamilySpin.model.GameScore;
import com.spin.FamilySpin.model.User;
import com.spin.FamilySpin.model.Game;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface GameScoreRepository extends JpaRepository<GameScore, Long> {
    Optional<GameScore> findByUserAndGame(User user, Game game);
    List<GameScore> findByWeekNumberOrderByScoreDesc(int weekNumber);
    List<GameScore> findByUserAndWeekNumber(User user, int weekNumber);
    List<GameScore> findByGameAndWeekNumber(Game game, int weekNumber);
}
