package com.gcf;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.IdTokenCredentials;
import com.google.auth.oauth2.IdTokenProvider;
import com.google.cloud.functions.BackgroundFunction;
import com.google.cloud.functions.Context;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import software.amazon.awssdk.auth.credentials.WebIdentityTokenFileCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleWithWebIdentityCredentialsProvider;
import software.amazon.awssdk.services.sts.model.AssumeRoleWithWebIdentityRequest;

import java.io.File;
import java.io.FileWriter;
import java.util.Arrays;
import java.util.logging.Logger;

import com.gcf.GcsToS3Function.GcsEvent;

public class GcsToS3Function implements BackgroundFunction<GcsEvent> {
	private static final Logger logger = Logger.getLogger(GcsToS3Function.class.getName());
	private static Storage storage = StorageOptions.getDefaultInstance().getService();
	private static final String TARGET_AUDIENCE = "https://sts.amazonaws.com/";
	private String token;

	@Override
	public void accept(GcsEvent event, Context context) {
		// AWS に接続するためのトークンを取得する
		getTokenFromMetadataServer();

		// 取得したトークンを AWS_WEB_IDENTITY_TOKEN_FILE に書き込む
		getToken();

		// S3 にファイルをアップロードする
		putS3Object(event);
	}

	private void getTokenFromMetadataServer() {
		try {
			GoogleCredentials googleCredentials = GoogleCredentials.getApplicationDefault();

			IdTokenCredentials idTokenCredentials = IdTokenCredentials.newBuilder()
					.setIdTokenProvider((IdTokenProvider) googleCredentials)
					.setTargetAudience(TARGET_AUDIENCE)
					.setOptions(Arrays.asList(IdTokenProvider.Option.FORMAT_FULL, IdTokenProvider.Option.LICENSES_TRUE))
					.build();
			token = idTokenCredentials.refreshAccessToken().getTokenValue();
			logger.info("Token: " + token);
		} catch (Exception e) {
			logger.info(e.getMessage());
			System.exit(1);
		}
	}

	private void getToken() {
		try {
			File file = new File(System.getenv("AWS_WEB_IDENTITY_TOKEN_FILE"));
			FileWriter fileWriter = new FileWriter(file);
			fileWriter.write(token);
			fileWriter.close();
		} catch (Exception e) {
			logger.info(e.getMessage());
			System.exit(1);
		}
	}

	private void putS3Object(GcsEvent event) {
		byte[] content = storage.readAllBytes(event.getBucket(), event.getName());
		try {
			StsClient stsClient = StsClient.builder()
					.region(Region.AWS_GLOBAL)
					.credentialsProvider(WebIdentityTokenFileCredentialsProvider.create())
					.build();
			AssumeRoleWithWebIdentityRequest roleRequest = AssumeRoleWithWebIdentityRequest.builder()
					.webIdentityToken(token)
					.roleArn(System.getenv("AWS_ROLE_ARN"))
					.roleSessionName(System.getenv("AWS_ROLE_SESSION_NAME"))
					.build();
			StsAssumeRoleWithWebIdentityCredentialsProvider provider = StsAssumeRoleWithWebIdentityCredentialsProvider.builder()
					.stsClient(stsClient)
					.refreshRequest(roleRequest)
					.build();
			S3Client client = S3Client.builder()
					.credentialsProvider(provider)
					.region(Region.US_EAST_1)
					.build();
			PutObjectRequest request = PutObjectRequest.builder()
					.bucket(System.getenv("AMAZON_S3_BUCKET"))
					.key(event.getName())
					.build();
			client.putObject(request, RequestBody.fromBytes(content));
		} catch (Exception e) {
			logger.info(e.getMessage());
			System.exit(1);
		}
	}

	public static class GcsEvent {
		private String bucket;
		private String name;

		public String getBucket() {
			return bucket;
		}

		public void setBucket(String bucket) {
			this.bucket = bucket;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}
}
