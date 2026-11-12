package com.freightfox.dispatch_load_balancer;

import com.freightfox.dispatch_load_balancer.util.HaversineCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

public class HaversineCalculatorTest {

        @Test
        @DisplayName("Same point returns zero distance")
        void samePoint_returnsZero() {
            double d = HaversineCalculator.distanceKm(28.6139, 28.6139, 77.2090, 77.2090);
            assertThat(d).isZero();
        }

        @Test
        @DisplayName("One degree of latitude is about 111.19 km")
        void oneDegreeOfLatitude() {
            double d = HaversineCalculator.distanceKm(0, 1, 0, 0);
            assertThat(d).isCloseTo(111.19, within(0.01));
        }

        @Test
        @DisplayName("Quarter of the equator is about 10007.54 km")
        void quarterOfEquator() {
            double d = HaversineCalculator.distanceKm(0, 0, 0, 90);
            assertThat(d).isCloseTo(10007.54, within(0.01));
        }

        @Test
        @DisplayName("Opposite points on Earth are about 20015.09 km apart and not NaN")
        void antipodalPoints() {
            double d = HaversineCalculator.distanceKm(0, 0, 0, 180);
            assertThat(d).isNotNaN();
            assertThat(d).isCloseTo(20015.09, within(0.01));
        }

        @Test
        @DisplayName("Distance works across the 180 degree longitude line")
        void crossesDateLine() {
            double d = HaversineCalculator.distanceKm(0, 0, 179, -179);
            assertThat(d).isCloseTo(222.39, within(0.01));
        }

        @Test
        @DisplayName("Chennai to Bengaluru is about 290 km")
        void chennaiToBengaluru() {
            double d = HaversineCalculator.distanceKm(13.0827, 12.9716, 80.2707, 77.5946);
            assertThat(d).isCloseTo(290.0, within(1.0));
        }

        @Test
        @DisplayName("Connaught Place to Karol Bagh (sample data) is about 14.4 km")
        void connaughtPlaceToKarolBagh() {
            double d = HaversineCalculator.distanceKm(28.6139, 28.7041, 77.2090, 77.1025);
            assertThat(d).isCloseTo(14.4, within(0.5));
        }

        @Test
        @DisplayName("Distance from A to B equals distance from B to A")
        void isSymmetric() {
            double ab = HaversineCalculator.distanceKm(28.5355, 28.4595, 77.3910, 77.0266);
            double ba = HaversineCalculator.distanceKm(28.4595, 28.5355, 77.0266, 77.3910);
            assertThat(ab).isCloseTo(ba, within(1e-9));
        }
    }
