/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package software.amazon.awssdk.arns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

public class ArnTest {

    @Test
    public void arnWithBasicResource_ParsesCorrectly() {
        String arnString = "arn:aws:s3:us-east-1:12345678910:myresource";
        Arn arn = Arn.fromString(arnString);
        assertThat(arn.partition()).isEqualTo("aws");
        assertThat(arn.service()).isEqualTo("s3");
        assertThat(arn.region()).hasValue("us-east-1");
        assertThat(arn.accountId()).hasValue("12345678910");
        assertThat(arn.resourceAsString()).isEqualTo("myresource");
    }

    @Test
    public void arnWithMinimalRequirementFromString() {
        Arn arn = Arn.fromString("arn:aws:foobar:::myresource");
        assertThat(arn.partition()).isEqualTo("aws");
        assertThat(arn.service()).isEqualTo("foobar");
        assertThat(arn.resourceAsString()).isEqualTo("myresource");
    }

    @Test
    public void arn_ParsesBackToString() {
        String arnString = "arn:aws:s3:us-east-1:12345678910:myresource";
        Arn arn = Arn.fromString(arnString);
        assertThat(arn.toString()).isEqualTo(arnString);
    }

    @Test
    public void arnWithQualifiedResource_ParsesBackToString() {
        String arnString = "arn:aws:s3:us-east-1:12345678910:myresource:foobar:1";
        Arn arn = Arn.fromString(arnString);
        assertThat(arn.toString()).isEqualTo(arnString);
        assertThat(arn.resourceAsString()).isEqualTo("myresource:foobar:1");
    }

    @Test
    public void arnWithResourceTypeAndResource_ParsesCorrectly() {
        String arnString = "arn:aws:s3:us-east-1:12345678910:bucket:foobar";
        Arn arn = Arn.fromString(arnString);
        assertThat(arn.partition()).isEqualTo("aws");
        assertThat(arn.service()).isEqualTo("s3");
        assertThat(arn.region()).hasValue("us-east-1");
        assertThat(arn.resourceAsString()).isEqualTo("bucket:foobar");
    }

    @Test
    public void arnWithResourceTypeAndResourceAndQualifier_ParsesCorrectly() {
        String arnString = "arn:aws:s3:us-east-1:12345678910:bucket:foobar:1";
        Arn arn = Arn.fromString(arnString);
        assertThat(arn.partition()).isEqualTo("aws");
        assertThat(arn.service()).isEqualTo("s3");
        assertThat(arn.region()).hasValue("us-east-1");
        assertThat(arn.resourceAsString()).isEqualTo("bucket:foobar:1");
    }

    @Test
    public void arnWithResourceTypeAndResource_SlashSplitter_ParsesCorrectly() {
        String arnString = "arn:aws:s3:us-east-1:12345678910:bucket/foobar";
        Arn arn = Arn.fromString(arnString);
        assertThat(arn.partition()).isEqualTo("aws");
        assertThat(arn.service()).isEqualTo("s3");
        assertThat(arn.region()).hasValue("us-east-1");
        assertThat(arn.resourceAsString()).isEqualTo("bucket/foobar");
    }   

    @Test
    public void oneArnEqualsEquivalentArn() {
        String arnString = "arn:aws:s3:us-east-1:12345678910:myresource:foobar";
        Arn arn1 = Arn.fromString(arnString);
        Arn arn2 = Arn.fromString(arnString);
        assertThat(arn1).isEqualTo(arn2);
        assertThat(arn1.resource()).isEqualTo(arn2.resource());
    }

    @Test
    public void arnFromBuilder_ParsesCorrectly() {
        Arn arn = Arn.builder()
                     .partition("aws")
                     .service("s3")
                     .region("us-east-1")
                     .accountId("123456789012")
                     .resource("bucket:foobar:1")
                     .build();

        assertThat(arn.partition()).isEqualTo("aws");
        assertThat(arn.service()).isEqualTo("s3");
        assertThat(arn.region()).hasValue("us-east-1");
        assertThat(arn.accountId()).hasValue("123456789012");
        assertThat(arn.resourceAsString()).isEqualTo("bucket:foobar:1");
    }

    @Test
    public void arnWithoutRegion_ParsesCorrectly() {
        String arnString = "arn:aws:s3::123456789012:myresource";
        Arn arn = Arn.fromString(arnString);
        assertThat(arn.partition()).isEqualTo("aws");
        assertThat(arn.service()).isEqualTo("s3");
        assertThat(arn.region()).isEmpty();
        assertThat(arn.accountId()).hasValue("123456789012");
        assertThat(arn.resourceAsString()).isEqualTo("myresource");
    }

    @Test
    public void arnWithoutAccountId_ParsesCorrectly() {
        String arnString = "arn:aws:s3:us-east-1::myresource";
        Arn arn = Arn.fromString(arnString);
        assertThat(arn.partition()).isEqualTo("aws");
        assertThat(arn.service()).isEqualTo("s3");
        assertThat(arn.region()).hasValue("us-east-1");
        assertThat(arn.accountId()).isEmpty();
        assertThat(arn.resourceAsString()).isEqualTo("myresource");
    }

    @Test
    public void toBuilder() {
        Arn oneArn = Arn.builder()
                      .partition("aws")
                      .service("s3")
                      .region("us-east-1")
                      .accountId("123456789012")
                      .resource("bucket:foobar:1")
                      .build();

        Arn anotherArn = oneArn.toBuilder().build();

        assertThat(oneArn).isEqualTo(anotherArn);
        assertThat(oneArn.hashCode()).isEqualTo(anotherArn.hashCode());
    }

    @Test
    public void hashCodeEquals() {
        Arn oneArn = Arn.builder()
                        .partition("aws")
                        .service("s3")
                        .region("us-east-1")
                        .accountId("123456789012")
                        .resource("bucket:foobar:1")
                        .build();

        Arn anotherArn = oneArn.toBuilder().region("somethingelse").build();
        assertThat(oneArn).isNotEqualTo(anotherArn);
        assertThat(oneArn.hashCode()).isNotEqualTo(anotherArn.hashCode());
    }

    @Test
    public void hashCodeEquals_minimalProperties() {
        Arn arn = Arn.builder()
                     .partition("aws")
                     .service("foobar")
                     .resource("resource")
                     .build();
        Arn anotherArn = arn.toBuilder().build();
        assertThat(arn.hashCode()).isEqualTo(anotherArn.hashCode());
        assertThat(arn.region()).isEmpty();
        assertThat(arn.accountId()).isEmpty();
        assertThat(arn.equals(anotherArn)).isTrue();
    }

    @Test
    public void arnWithoutPartition_ThrowsIllegalArgumentException() {
        String arnString = "arn::s3:us-east-1:12345678910:myresource";
        assertThatThrownBy(() -> Arn.fromString(arnString)).hasMessageContaining("artition must not be blank or empty.");
    }

    @Test
    public void arnWithoutService_ThrowsIllegalArgumentException() {
        String arnString = "arn:aws::us-east-1:12345678910:myresource";
        assertThatThrownBy(() -> Arn.fromString(arnString)).hasMessageContaining("service must not be blank or empty");
    }

    @Test
    public void arnWithoutResource_ThrowsIllegalArgumentException() {
        String arnString = "arn:aws:s3:us-east-1:12345678910:";
        assertThatThrownBy(() -> Arn.fromString(arnString)).hasMessageContaining("Malformed ARN");
    }

    @Test
    public void invalidArn_ThrowsIllegalArgumentException() {
        String arnString = "arn:aws:";
        assertThatThrownBy(() -> Arn.fromString(arnString)).hasMessageContaining("Malformed ARN");
    }

    @Test
    public void arnDoesntStartWithArn_ThrowsIllegalArgumentException() {
        String arnString = "fakearn:aws:";
        assertThatThrownBy(() -> Arn.fromString(arnString)).hasMessageContaining("Malformed ARN");
    }

    @Test
    public void invalidArnWithoutPartition_ThrowsIllegalArgumentException() {
        String arnString = "arn:";
        assertThatThrownBy(() -> Arn.fromString(arnString)).hasMessageContaining("Malformed ARN");
    }

    @Test
    public void invalidArnWithoutService_ThrowsIllegalArgumentException() {
        String arnString = "arn:aws:";
        assertThatThrownBy(() -> Arn.fromString(arnString)).hasMessageContaining("Malformed ARN");
    }

    @Test
    public void invalidArnWithoutRegion_ThrowsIllegalArgumentException() {
        String arnString = "arn:aws:s3:";
        assertThatThrownBy(() -> Arn.fromString(arnString)).hasMessageContaining("Malformed ARN");
    }

    @Test
    public void invalidArnWithoutAccountId_ThrowsIllegalArgumentException() {
        String arnString = "arn:aws:s3:us-east-1:";
        assertThatThrownBy(() -> Arn.fromString(arnString)).hasMessageContaining("Malformed ARN");
    }
}
