# 概要
Cloud Run Functions の Cloud Storage トリガーで Cloud Storage に配置されたファイルを Amazon S3 に格納するソースコード

ソースコードの詳細などは Qiita に記載しています

https://qiita.com/ysmmb12/items/8e8c4ca1e8ed8528d39f

# 環境
 - Cloud Run Functions 第一世代

# 使い方
 - Cloud Run Functions の関数で使用するサービスアカウントを作成する
 - 作成したサービスアカウントから AWS へ接続できるように、AWS IAM ロールを作成する
   - 上記2工程は以下ブログを参照
     - https://aws.amazon.com/jp/blogs/security/access-aws-using-a-google-cloud-platform-native-workload-identity/
     - https://qiita.com/ysmmb12/items/8e8c4ca1e8ed8528d39f
 - 以下の設定で Cloud Run Functions を作成する
   - トリガー
     - トリガーのタイプ：Cloud Storage
     - イベントタイプ：google.cloud.storage.object.v1.finalized
     - バケット：任意のバケット
   - ランタイム
     - ランタイム環境変数
       - AWS_ROLE_SESSION_NAME：AWS IAM ロールへの接続元を特定する文字列を指定、識別するためなので任意の値を入力
       - AWS_ROLE_ARN：使用する AWS IAM Role の ARN を指定、形式は `arn:aws:iam::<アカウントID>:role/＜IAM Role 名＞`
       - AWS_WEB_IDENTITY_TOKEN_FILE：AWS への認証で使用するトークンを書くファイルパスを指定
       - AMAZON_S3_BUCKET：ファイルを格納する Amazon S3 バケット名
   - このリポジトリのソースコードと pom.xml を Cloud Run Functions に指定する