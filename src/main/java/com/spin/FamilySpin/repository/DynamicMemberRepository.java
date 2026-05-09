package com.spin.FamilySpin.repository;

import com.spin.FamilySpin.model.DynamicMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DynamicMemberRepository extends JpaRepository<DynamicMember, Long> {
    Optional<DynamicMember> findByName(String name);
    Optional<DynamicMember> findByNameIgnoreCase(String name);
    void deleteByNameIgnoreCase(String name);
}
