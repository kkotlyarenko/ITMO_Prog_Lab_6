package main.models;

import main.utils.InvalidValueException;
import main.utils.Validatable;

import java.io.Serializable;
import java.util.Objects;

public class Coordinates implements Validatable, Serializable {
    private Long x; //Поле не может быть null
    private double y; //Максимальное значение поля: 716

    public Long getX() {
        return x;
    }

    public void setX(Long x) {
        if (x == null) throw new InvalidValueException("x", "поле не может быть null");
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        if (y > 716) throw new InvalidValueException("y", "максимальное значение поля: 716");
        this.y = y;
    }

    @Override
    public String toString() {
        return "x: " + x +
                ", y: " + y;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Coordinates that = (Coordinates) o;
        return Double.compare(that.y, y) == 0 && x.equals(that.x);
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    @Override
    public void validate() {
        if (x == null) throw new InvalidValueException("x", "поле не может быть null");
        if (y > 716) throw new InvalidValueException("y", "максимальное значение поля: 716");
    }
}
