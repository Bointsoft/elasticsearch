/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.license;

import java.util.Objects;

/**
 * A base class for checking licensed features against the license.
 */
public abstract class LicensedFeature {

    /**
     * A Momentary feature is one that is tracked at the moment the license is checked.
     */
    public static class Momentary extends LicensedFeature {

        private Momentary(String name, License.OperationMode minimumOperationMode, boolean needsActive) {
            super(name, minimumOperationMode, needsActive);
        }

        /**
         * Checks whether the feature is allowed by the given license state, and
         * updates the last time the feature was used.
         */
        public boolean check(XPackLicenseState state) {
            if (state.isAllowed(this)) {
                state.featureUsed(this);
                return true;
            } else {
                return false;
            }
        }
    }

    /**
     * A Persistent feature is one that is tracked starting when the license is checked, and later may be untracked.
     */
    public static class Persistent extends LicensedFeature {
        private Persistent(String name, License.OperationMode minimumOperationMode, boolean needsActive) {
            super(name, minimumOperationMode, needsActive);
        }

        /**
         * Checks whether the feature is allowed by the given license state, and
         * begins tracking the feature as "on" for the given context.
         */
        public boolean checkAndStartTracking(XPackLicenseState state, String contextName) {
            if (state.isAllowed(this)) {
                state.enableUsageTracking(this, contextName);
                return true;
            } else {
                return false;
            }
        }

        /**
         * Stop tracking the feature so that the current time will be the last that it was used.
         */
        public void stopTracking(XPackLicenseState state, String contextName) {
            state.disableUsageTracking(this, contextName);
        }
    }

    final String name;
    final License.OperationMode minimumOperationMode;
    final boolean needsActive;

    public LicensedFeature(String name, License.OperationMode minimumOperationMode, boolean needsActive) {
        this.name = name;
        this.minimumOperationMode = minimumOperationMode;
        this.needsActive = needsActive;
    }

    /** Create a momentary feature for hte given license level */
    public static Momentary momentary(String name, License.OperationMode licenseLevel) {
        return new Momentary(name, licenseLevel, true);
    }

    /** Create a persistent feature for the given license level */
    public static Persistent persistent(String name, License.OperationMode licenseLevel) {
        return new Persistent(name, licenseLevel, true);
    }

    /**
     * Creates a momentary feature, but one that is lenient as
     * to whether the license needs to be active to allow the feature.
     */
    @Deprecated
    public static Momentary momentaryLenient(String name, License.OperationMode licenseLevel) {
        return new Momentary(name, licenseLevel, false);
    }

    /**
     * Creates a persistent feature, but one that is lenient as
     * to whether the license needs to be active to allow the feature.
     */
    @Deprecated
    public static Persistent persistentLenient(String name, License.OperationMode licenseLevel) {
        return new Persistent(name, licenseLevel, false);
    }

    /**
     * Returns whether the feature is allowed by the current license
     * without affecting feature tracking.
     */
    public final boolean checkWithoutTracking(XPackLicenseState state) {
        return state.isAllowed(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LicensedFeature that = (LicensedFeature) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
