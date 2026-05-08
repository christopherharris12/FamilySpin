package com.spin.FamilySpin.repository;

import com.spin.FamilySpin.model.GameQuestion;
import com.spin.FamilySpin.model.Game;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GameQuestionRepository extends JpaRepository<GameQuestion, Long> {
    List<GameQuestion> findByGame(Game game);
    List<GameQuestion> findByGameAndCategory(Game game, String category);
}
