package com.ibm.opportunity_analysis_service.application.service;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CalcularSimilitudService {

    public double calcular(List<Float> vectorA, List<Float> vectorB) {

        if (vectorA == null || vectorB == null) {
            throw new IllegalArgumentException("Los vectores no pueden ser null");
        }

        if (vectorA.isEmpty() || vectorB.isEmpty()) {
            throw new IllegalArgumentException("Los vectores no pueden estar vacios");
        }

        if (vectorA.size() != vectorB.size()) {
            throw new IllegalArgumentException("Los vectores deben tener la misma cantidad de dimensiones");
        }

        double productoPunto = 0.0;
        double magnitudA = 0.0;
        double magnitudB = 0.0;

        for (int i = 0; i < vectorA.size(); i++) {
            double valorA = vectorA.get(i);
            double valorB = vectorB.get(i);

            productoPunto += valorA * valorB;
            magnitudA += valorA * valorA;
            magnitudB += valorB * valorB;
        }

        if (magnitudA == 0.0 || magnitudB == 0.0) {
            throw new IllegalArgumentException("No se puede calcular la similitud con un vector de magnitud cero");
        }

        return productoPunto / (Math.sqrt(magnitudA) * Math.sqrt(magnitudB));
    }
}