package ru.sportclub.workouts.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sportclub.workouts.entity.CatalogExercise;

import java.util.List;

public interface CatalogExerciseRepository extends JpaRepository<CatalogExercise, String> {
    List<CatalogExercise> findAllByOrderByMuscleGroupAscNameAsc();
}
