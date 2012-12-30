/*
 * This file is part of DependencyCheck.
 *
 * DependencyCheck is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * DependencyCheck is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * DependencyCheck. If not, see http://www.gnu.org/licenses/.
 *
 * Copyright (c) 2012 Jeremy Long. All Rights Reserved.
 */
package org.codesecure.dependencycheck.data.nvdcve.generated;

//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, vJAXB 2.1.10 in JDK 6
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a>
// Any modifications to this file will be lost upon recompilation of the source schema.
// Generated on: 2012.10.21 at 11:58:46 AM EDT
//
import javax.annotation.Generated;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlValue;

/**
 * <p>Java class for accessComplexityType complex type.
 *
 * <p>The following schema fragment specifies the expected content contained
 * within this class.
 *
 * <pre>
 * &lt;complexType name="accessComplexityType">
 *   &lt;simpleContent>
 *     &lt;extension base="&lt;http://scap.nist.gov/schema/cvss-v2/0.2>accessComplexityEnumType">
 *       &lt;attGroup ref="{http://scap.nist.gov/schema/cvss-v2/0.2}vectorAttributeGroup"/>
 *     &lt;/extension>
 *   &lt;/simpleContent>
 * &lt;/complexType>
 * </pre>
 *
 *
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "accessComplexityType", propOrder = {
    "value"
})
@Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2012-10-21T11:58:46-04:00", comments = "JAXB RI vJAXB 2.1.10 in JDK 6")
public class AccessComplexityType {

    @XmlValue
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2012-10-21T11:58:46-04:00", comments = "JAXB RI vJAXB 2.1.10 in JDK 6")
    protected AccessComplexityEnumType value;
    @XmlAttribute
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2012-10-21T11:58:46-04:00", comments = "JAXB RI vJAXB 2.1.10 in JDK 6")
    protected Boolean approximated;

    /**
     * Gets the value of the value property.
     *
     * @return possible object is
     *     {@link AccessComplexityEnumType }
     *
     */
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2012-10-21T11:58:46-04:00", comments = "JAXB RI vJAXB 2.1.10 in JDK 6")
    public AccessComplexityEnumType getValue() {
        return value;
    }

    /**
     * Sets the value of the value property.
     *
     * @param value allowed object is
     *     {@link AccessComplexityEnumType }
     *
     */
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2012-10-21T11:58:46-04:00", comments = "JAXB RI vJAXB 2.1.10 in JDK 6")
    public void setValue(AccessComplexityEnumType value) {
        this.value = value;
    }

    /**
     * Gets the value of the approximated property.
     *
     * @return possible object is
     *     {@link Boolean }
     *
     */
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2012-10-21T11:58:46-04:00", comments = "JAXB RI vJAXB 2.1.10 in JDK 6")
    public boolean isApproximated() {
        if (approximated == null) {
            return false;
        } else {
            return approximated;
        }
    }

    /**
     * Sets the value of the approximated property.
     *
     * @param value allowed object is
     *     {@link Boolean }
     *
     */
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2012-10-21T11:58:46-04:00", comments = "JAXB RI vJAXB 2.1.10 in JDK 6")
    public void setApproximated(Boolean value) {
        this.approximated = value;
    }
}
