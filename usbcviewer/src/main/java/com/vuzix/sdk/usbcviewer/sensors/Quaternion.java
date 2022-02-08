// From https://github.com/google-ar/sceneform-android-sdk/blob/master/sceneformsrc/sceneform/src/main/java/com/google/ar/sceneform/math/Quaternion.java
// Portions of that project use the Apache 2.0 License, but no assertion was found relating to
// this content.

package com.vuzix.sdk.usbcviewer.sensors;

/**
 * A Sceneform quaternion class for floats.
 *
 * <p>Quaternion operations are Hamiltonian using the right-hand-rule convention.
 */
public class Quaternion {
    public float x;
    public float y;
    public float z;
    public float w;

    /** Construct Quaternion and set to Identity */
    @SuppressWarnings("initialization") // Suppress @UnderInitialization warning.
    public Quaternion() {
        x = 0;
        y = 0;
        z = 0;
        w = 1;
    }

    /**
     * Construct Quaternion and set each value. The Quaternion will be normalized during construction
     */
    @SuppressWarnings("initialization") // Suppress @UnderInitialization warning.
    public Quaternion(float x, float y, float z, float w) {
        set(x, y, z, w);
    }

    /** Construct Quaternion using values from another Quaternion */
    @SuppressWarnings("initialization") // Suppress @UnderInitialization warning.
    public Quaternion(Quaternion q) {
        set(q);
    }

    /** Copy values from another Quaternion into this one */
    public void set(Quaternion q) {
        x = q.x;
        y = q.y;
        z = q.z;
        w = q.w;
        normalize();
    }

    /** Set each value and normalize the Quaternion */
    public void set(float qx, float qy, float qz, float qw) {
        x = qx;
        y = qy;
        z = qz;
        w = qw;
        normalize();
    }

    /** Set the Quaternion to identity */
    public void setIdentity() {
        x = 0;
        y = 0;
        z = 0;
        w = 1;
    }

    private static final float FLT_EPSILON = 1.19209290E-07f;
    private static final float MAX_DELTA = 1.0E-10f;

    /**
     * Returns true if two floats are equal within a tolerance. Useful for comparing floating point
     * numbers while accounting for the limitations in floating point precision.
     */
    // https://randomascii.wordpress.com/2012/02/25/comparing-floating-point-numbers-2012-edition/
    private static boolean almostEqualRelativeAndAbs(float a, float b) {
        // Check if the numbers are really close -- needed
        // when comparing numbers near zero.
        float diff = Math.abs(a - b);
        if (diff <= MAX_DELTA) {
            return true;
        }

        a = Math.abs(a);
        b = Math.abs(b);
        float largest = Math.max(a, b);

        if (diff <= largest * FLT_EPSILON) {
            return true;
        }
        return false;
    }

    /**
     * Rescales the quaternion to the unit length.
     *
     * <p>If the Quaternion can not be scaled, it is set to identity and false is returned.
     *
     * @return true if the Quaternion was non-zero
     */
    public boolean normalize() {
        float normSquared = Quaternion.dot(this, this);
        if (almostEqualRelativeAndAbs(normSquared, 0.0f)) {
            setIdentity();
            return false;
        } else if (normSquared != 1) {
            float norm = (float) (1.0 / Math.sqrt(normSquared));
            x *= norm;
            y *= norm;
            z *= norm;
            w *= norm;
        } else {
            // do nothing if normSquared is already the unit length
        }
        return true;
    }

    /**
     * Get a Quaternion with a matching rotation but scaled to unit length.
     *
     * @return the quaternion scaled to the unit length, or zero if that can not be done.
     */
    public Quaternion normalized() {
        Quaternion result = new Quaternion(this);
        result.normalize();
        return result;
    }

    /**
     * Get a Quaternion with the opposite rotation
     *
     * @return the opposite rotation
     */
    public Quaternion inverted() {
        return new Quaternion(-this.x, -this.y, -this.z, this.w);
    }

    /**
     * Flips the sign of the Quaternion, but represents the same rotation.
     *
     * @return the negated Quaternion
     */
    Quaternion negated() {
        return new Quaternion(-this.x, -this.y, -this.z, -this.w);
    }

    @Override
    public String toString() {
        return "[x=" + x + ", y=" + y + ", z=" + z + ", w=" + w + "]";
    }


    /**
     * Create a Quaternion by combining two Quaternions multiply(lhs, rhs) is equivalent to performing
     * the rhs rotation then lhs rotation Ordering is important for this operation.
     *
     * @return The combined rotation
     */
    public static Quaternion multiply(Quaternion lhs, Quaternion rhs) {
        float lx = lhs.x;
        float ly = lhs.y;
        float lz = lhs.z;
        float lw = lhs.w;
        float rx = rhs.x;
        float ry = rhs.y;
        float rz = rhs.z;
        float rw = rhs.w;

        Quaternion result =
                new Quaternion(
                        lw * rx + lx * rw + ly * rz - lz * ry,
                        lw * ry - lx * rz + ly * rw + lz * rx,
                        lw * rz + lx * ry - ly * rx + lz * rw,
                        lw * rw - lx * rx - ly * ry - lz * rz);
        return result;
    }

    /**
     * Uniformly scales a Quaternion without normalizing
     *
     * @return a Quaternion multiplied by a scalar amount.
     */
    Quaternion scaled(float a) {
        Quaternion result = new Quaternion();
        result.x = this.x * a;
        result.y = this.y * a;
        result.z = this.z * a;
        result.w = this.w * a;

        return result;
    }

    /**
     * Adds two Quaternion's without normalizing
     *
     * @return The combined Quaternion
     */
    static Quaternion add(Quaternion lhs, Quaternion rhs) {
        Quaternion result = new Quaternion();
        result.x = lhs.x + rhs.x;
        result.y = lhs.y + rhs.y;
        result.z = lhs.z + rhs.z;
        result.w = lhs.w + rhs.w;
        return result;
    }

    /** The dot product of two Quaternions. */
    static float dot(Quaternion lhs, Quaternion rhs) {
        return lhs.x * rhs.x + lhs.y * rhs.y + lhs.z * rhs.z + lhs.w * rhs.w;
    }

    /**
     * Get a new Quaternion using an axis/angle to define the rotation
     *
     * @param axis_x Sets rotation direction
     * @param axis_y Sets rotation direction
     * @param axis_z Sets rotation direction
     * @param degrees Angle size in degrees
     */
    public static Quaternion axisAngle(float axis_x, float axis_y, float axis_z, float degrees) {
        Quaternion dest = new Quaternion();
        double angle = Math.toRadians(degrees);
        double factor = Math.sin(angle / 2.0);

        dest.x = (float) (axis_x * factor);
        dest.y = (float) (axis_y * factor);
        dest.z = (float) (axis_z * factor);
        dest.w = (float) Math.cos(angle / 2.0);
        dest.normalize();
        return dest;
    }

    /**
     * Compare two Quaternions
     *
     * <p>Tests for equality by calculating the dot product of lhs and rhs. lhs and -lhs will not be
     * equal according to this function.
     */
    public static boolean equals(Quaternion lhs, Quaternion rhs) {
        float dot = Quaternion.dot(lhs, rhs);
        return almostEqualRelativeAndAbs(dot, 1.0f);
    }


    /**
     * Returns true if the other object is a Quaternion and the dot product is 1.0 +/- a tolerance.
     */
    @Override
    @SuppressWarnings("override.param.invalid")
    public boolean equals(Object other) {
        if (!(other instanceof Quaternion)) {
            return false;
        }
        if (this == other) {
            return true;
        }
        return Quaternion.equals(this, (Quaternion) other);
    }

    /** @hide */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Float.floatToIntBits(w);
        result = prime * result + Float.floatToIntBits(x);
        result = prime * result + Float.floatToIntBits(y);
        result = prime * result + Float.floatToIntBits(z);
        return result;
    }

    /** Get a Quaternion set to identity */
    public static Quaternion identity() {
        return new Quaternion();
    }
}