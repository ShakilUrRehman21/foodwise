package com.foodwise.scan;

import com.foodwise.common.enums.Unit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptTextParserTest {

    private static final String BLINKIT = String.join("\n",
            "BLINKIT", "Tax Invoice", "Invoice No: BLK-99812   Date: 08/11/2026", "Item                Qty   Rate   Amount",
            "1. Amul Taaza Toned Milk 500 ml   2   27.00   54.00", "Tomato Hybrid 1 kg 2 x 38.00 76.00",
            "Coriander Leaves 100 g          15.00", "Tata Sampann Toor Dal 1kg  1  175.00  175.00",
            "Potato 1.250 32.00 40.00", "Amul Fresh Paneer 200g", "1 95.00 95.00",
            "Sub Total 455.00", "CGST 2.5% 5.69", "Delivery Fee 25.00", "Grand Total 480.00", "Paid via UPI", "Thank you!");

    @Test
    void parsesHeaderFields() {
        var r = ReceiptTextParser.parse(BLINKIT);
        assertThat(r.storeName()).isEqualTo("Blinkit");
        assertThat(r.purchaseDate()).isEqualTo(LocalDate.of(2026, 11, 8));
        assertThat(r.totalAmount()).isEqualByComparingTo("480");
    }

    @Test
    void parsesItemLayoutsAndSkipsTotalsTaxesFees() {
        var lines = ReceiptTextParser.parse(BLINKIT).lines();
        assertThat(lines).hasSize(6);
        assertThat(lines).noneMatch(l -> l.name().toLowerCase().matches(".*(gst|fee|total|upi).*"));

        var milk = lines.get(0); // pack size x qty column
        assertThat(milk.quantity()).isEqualByComparingTo("1000");
        assertThat(milk.unit()).isEqualTo(Unit.ML);
        assertThat(milk.price()).isEqualByComparingTo("54");

        var tomato = lines.get(1); // "2 x 38.00" multiplier
        assertThat(tomato.quantity()).isEqualByComparingTo("2");
        assertThat(tomato.unit()).isEqualTo(Unit.KG);
        assertThat(tomato.price()).isEqualByComparingTo("76");

        var potato = lines.get(4); // weighed item with 3 decimals
        assertThat(potato.quantity()).isEqualByComparingTo("1.25");
        assertThat(potato.unit()).isEqualTo(Unit.KG);

        var paneer = lines.get(5); // two-line item
        assertThat(paneer.name()).contains("Paneer");
        assertThat(paneer.quantity()).isEqualByComparingTo("200");
        assertThat(paneer.price()).isEqualByComparingTo("95");
    }

    @Test
    void toleratesOcrNoise() {
        var r = ReceiptTextParser.parse("ONION 2KG   1  7O.OO  7O.OO   ;\nAMUL MALAI PANEER 200G 1 95.00 95.00     \\");
        assertThat(r.lines()).hasSize(2);
        assertThat(r.lines().get(0).price()).isEqualByComparingTo(new BigDecimal("70"));
        assertThat(r.lines().get(1).price()).isEqualByComparingTo(new BigDecimal("95"));
    }

    @Test
    void crossChecksQuantityTimesRateAgainstAmount() {
        var bad = ReceiptTextParser.parse("ONION 2KG 1 78.00 728.00").lines().getFirst();
        assertThat(bad.price()).isEqualByComparingTo("78");
        assertThat(bad.suspicious()).isTrue();
        var good = ReceiptTextParser.parse("TOMATO HYBRID 1KG 2 38.00 76.00").lines().getFirst();
        assertThat(good.price()).isEqualByComparingTo("76");
        assertThat(good.suspicious()).isFalse();
    }

    @Test
    void emptyInputYieldsNothing() {
        assertThat(ReceiptTextParser.parse("").lines()).isEmpty();
        assertThat(ReceiptTextParser.parse(null).lines()).isEmpty();
    }
}
