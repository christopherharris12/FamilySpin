package com.spin.FamilySpin.repository;

import com.spin.FamilySpin.model.GameAnswer;
import com.spin.FamilySpin.model.User;
import com.spin.FamilySpin.model.Game;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface GameAnswerRepository extends JpaRepository<GameAnswer, Long> {
    List<GameAnswer> findByUserAndGameAndAnswerDate(User user, Game game, LocalDate answerDate);
    
    Long countByUserAndGameAndAnswerDate(User user, Game game, LocalDate answerDate);
}
