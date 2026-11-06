package ru.sportclub.members.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sportclub.members.entity.MembershipPlan;

import java.util.List;

public interface MembershipPlanRepository extends JpaRepository<MembershipPlan, String> {
    List<MembershipPlan> findAllByOrderByPriceRubAscIdAsc();
}
