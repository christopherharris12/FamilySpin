package com.spin.FamilySpin.repository;

import com.spin.FamilySpin.model.GameChallenge;
import com.spin.FamilySpin.model.Game;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GameChallengeRepository extends JpaRepository<GameChallenge, Long> {
    List<GameChallenge> findByGame(Game game);
    List<GameChallenge> findByGameAndType(Game game, String type);
}
