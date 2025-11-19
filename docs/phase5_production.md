# Phase 5: 프로덕션 배포 (Production Deployment)

**기간**: 진행 중 (Ongoing)
**목표**: 실제 서비스 배포 및 취업 활동
**시간 배분**: Backend 취업 준비 70% : Music AI 서비스 운영 30%

---

## 📋 Phase 5 개요

이 단계는 실제 프로덕션 환경에 배포하고, 포트폴리오를 활용하여 취업 활동을 하는 단계입니다.

### 목표
1. AWS/GCP 클라우드 배포
2. CI/CD 파이프라인 구축
3. 모니터링 및 로깅
4. 성능 최적화
5. 포트폴리오 완성 및 취업 준비

---

## 🗓️ 주차별 학습 계획

### Week 1-2: 클라우드 인프라 구축

#### 학습 목표
- AWS 기본 서비스 이해
- EC2, RDS, S3 구축
- 비용 최적화

#### 1. AWS 아키텍처 설계

```
┌─────────────────────────────────────────────────────────┐
│                   CloudFront (CDN)                       │
│  - React 정적 파일                                       │
│  - MIDI 파일 캐싱                                        │
└────────────┬────────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────┐
│                 Application Load Balancer                │
│  - HTTPS 리다이렉션                                      │
│  - SSL/TLS 인증서                                        │
└─────┬───────────────────┬────────────────────────────────┘
      │                   │
      ▼                   ▼
┌──────────────┐   ┌──────────────┐
│  ECS Fargate  │   │  ECS Fargate  │
│  (Backend)    │   │  (ML Server)  │
│  - Spring     │   │  - FastAPI    │
│  - Auto Scale │   │  - GPU (옵션)│
└──────┬───────┘   └───────┬───────┘
       │                   │
       ▼                   ▼
┌─────────────────────────────────────┐
│           RDS PostgreSQL             │
│  - Multi-AZ                          │
│  - Automated Backup                  │
└──────────────────────────────────────┘

┌─────────────────────────────────────┐
│          ElastiCache Redis           │
│  - Cluster Mode                      │
└──────────────────────────────────────┘

┌─────────────────────────────────────┐
│               S3 Bucket              │
│  - MIDI files                        │
│  - Static assets                     │
└──────────────────────────────────────┘
```

#### 2. Terraform으로 IaC 구축

**main.tf**:
```hcl
terraform {
  required_version = ">= 1.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  backend "s3" {
    bucket = "charlie-parker-terraform-state"
    key    = "prod/terraform.tfstate"
    region = "ap-northeast-2"
  }
}

provider "aws" {
  region = var.aws_region
}

# VPC
resource "aws_vpc" "main" {
  cidr_block           = "10.0.0.0/16"
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = {
    Name = "charlie-parker-vpc"
  }
}

# Subnets
resource "aws_subnet" "public_1" {
  vpc_id                  = aws_vpc.main.id
  cidr_block              = "10.0.1.0/24"
  availability_zone       = "${var.aws_region}a"
  map_public_ip_on_launch = true

  tags = {
    Name = "charlie-parker-public-1"
  }
}

resource "aws_subnet" "public_2" {
  vpc_id                  = aws_vpc.main.id
  cidr_block              = "10.0.2.0/24"
  availability_zone       = "${var.aws_region}c"
  map_public_ip_on_launch = true

  tags = {
    Name = "charlie-parker-public-2"
  }
}

resource "aws_subnet" "private_1" {
  vpc_id            = aws_vpc.main.id
  cidr_block        = "10.0.11.0/24"
  availability_zone = "${var.aws_region}a"

  tags = {
    Name = "charlie-parker-private-1"
  }
}

resource "aws_subnet" "private_2" {
  vpc_id            = aws_vpc.main.id
  cidr_block        = "10.0.12.0/24"
  availability_zone = "${var.aws_region}c"

  tags = {
    Name = "charlie-parker-private-2"
  }
}

# Internet Gateway
resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name = "charlie-parker-igw"
  }
}

# RDS PostgreSQL
resource "aws_db_instance" "postgres" {
  identifier        = "charlie-parker-db"
  engine            = "postgres"
  engine_version    = "15.4"
  instance_class    = "db.t3.micro"  # 프리티어
  allocated_storage = 20

  db_name  = "charlie_parker"
  username = var.db_username
  password = var.db_password

  vpc_security_group_ids = [aws_security_group.rds.id]
  db_subnet_group_name   = aws_db_subnet_group.main.name

  multi_az               = false  # 비용 절감 (운영 환경에선 true)
  backup_retention_period = 7
  skip_final_snapshot    = true

  tags = {
    Name = "charlie-parker-postgres"
  }
}

resource "aws_db_subnet_group" "main" {
  name       = "charlie-parker-db-subnet"
  subnet_ids = [aws_subnet.private_1.id, aws_subnet.private_2.id]

  tags = {
    Name = "charlie-parker-db-subnet-group"
  }
}

# ElastiCache Redis
resource "aws_elasticache_cluster" "redis" {
  cluster_id           = "charlie-parker-redis"
  engine               = "redis"
  node_type            = "cache.t3.micro"  # 프리티어
  num_cache_nodes      = 1
  parameter_group_name = "default.redis7"
  port                 = 6379
  subnet_group_name    = aws_elasticache_subnet_group.main.name
  security_group_ids   = [aws_security_group.redis.id]

  tags = {
    Name = "charlie-parker-redis"
  }
}

resource "aws_elasticache_subnet_group" "main" {
  name       = "charlie-parker-redis-subnet"
  subnet_ids = [aws_subnet.private_1.id, aws_subnet.private_2.id]
}

# S3 Bucket for MIDI files
resource "aws_s3_bucket" "midi_files" {
  bucket = "charlie-parker-midi-files"

  tags = {
    Name = "charlie-parker-midi"
  }
}

resource "aws_s3_bucket_public_access_block" "midi_files" {
  bucket = aws_s3_bucket.midi_files.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# ECS Cluster
resource "aws_ecs_cluster" "main" {
  name = "charlie-parker-cluster"

  setting {
    name  = "containerInsights"
    value = "enabled"
  }

  tags = {
    Name = "charlie-parker-ecs"
  }
}

# Security Groups
resource "aws_security_group" "alb" {
  name        = "charlie-parker-alb-sg"
  description = "Security group for ALB"
  vpc_id      = aws_vpc.main.id

  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "charlie-parker-alb-sg"
  }
}

resource "aws_security_group" "ecs_tasks" {
  name        = "charlie-parker-ecs-tasks-sg"
  description = "Security group for ECS tasks"
  vpc_id      = aws_vpc.main.id

  ingress {
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  ingress {
    from_port       = 8000
    to_port         = 8000
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "charlie-parker-ecs-tasks-sg"
  }
}

resource "aws_security_group" "rds" {
  name        = "charlie-parker-rds-sg"
  description = "Security group for RDS"
  vpc_id      = aws_vpc.main.id

  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs_tasks.id]
  }

  tags = {
    Name = "charlie-parker-rds-sg"
  }
}

resource "aws_security_group" "redis" {
  name        = "charlie-parker-redis-sg"
  description = "Security group for Redis"
  vpc_id      = aws_vpc.main.id

  ingress {
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs_tasks.id]
  }

  tags = {
    Name = "charlie-parker-redis-sg"
  }
}

# Outputs
output "rds_endpoint" {
  value = aws_db_instance.postgres.endpoint
}

output "redis_endpoint" {
  value = aws_elasticache_cluster.redis.cache_nodes[0].address
}

output "s3_bucket_name" {
  value = aws_s3_bucket.midi_files.id
}
```

**실행**:
```bash
# Terraform 초기화
terraform init

# 계획 확인
terraform plan

# 적용
terraform apply

# 인프라 확인
terraform show
```

#### 3. 비용 최적화 전략

**프리티어 활용** (12개월):
- EC2: t2.micro (월 750시간)
- RDS: db.t2.micro (월 750시간)
- S3: 5GB
- CloudFront: 50GB 전송량

**예상 비용** (프리티어 이후):
```
월간 예상 비용:

1. ECS Fargate
   - Backend (0.25 vCPU, 0.5GB): $10/월
   - ML Server (0.5 vCPU, 1GB): $15/월

2. RDS (db.t3.micro): $15/월

3. ElastiCache (cache.t3.micro): $12/월

4. S3 + CloudFront: $5/월

5. Load Balancer: $16/월

총: 약 $73/월 (₩95,000)

---

비용 절감 방법:
- EC2 Reserved Instance (1년 약정 시 40% 절감)
- Auto Scaling으로 야간 인스턴스 축소
- CloudFront 캐싱 최적화
- 개발 환경은 로컬 Docker 사용
```

---

### Week 3-4: CI/CD 파이프라인

#### 학습 목표
- GitHub Actions 설정
- 자동 배포 파이프라인
- 무중단 배포

#### 1. GitHub Actions Workflow

**.github/workflows/deploy-backend.yml**:
```yaml
name: Deploy Backend to AWS ECS

on:
  push:
    branches:
      - main
    paths:
      - 'src/**'
      - 'build.gradle'
      - 'Dockerfile'

env:
  AWS_REGION: ap-northeast-2
  ECR_REPOSITORY: charlie-parker-backend
  ECS_CLUSTER: charlie-parker-cluster
  ECS_SERVICE: backend-service
  ECS_TASK_DEFINITION: backend-task-definition

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout code
        uses: actions/checkout@v3

      - name: Set up JDK 21
        uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Grant execute permission for gradlew
        run: chmod +x gradlew

      - name: Build with Gradle
        run: ./gradlew clean build

      - name: Run tests
        run: ./gradlew test

      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v2
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-region: ${{ env.AWS_REGION }}

      - name: Login to Amazon ECR
        id: login-ecr
        uses: aws-actions/amazon-ecr-login@v1

      - name: Build and push Docker image
        env:
          ECR_REGISTRY: ${{ steps.login-ecr.outputs.registry }}
          IMAGE_TAG: ${{ github.sha }}
        run: |
          docker build -t $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG .
          docker push $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG
          docker tag $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG $ECR_REGISTRY/$ECR_REPOSITORY:latest
          docker push $ECR_REGISTRY/$ECR_REPOSITORY:latest

      - name: Update ECS task definition
        id: task-def
        uses: aws-actions/amazon-ecs-render-task-definition@v1
        with:
          task-definition: ${{ env.ECS_TASK_DEFINITION }}
          container-name: backend
          image: ${{ steps.login-ecr.outputs.registry }}/${{ env.ECR_REPOSITORY }}:${{ github.sha }}

      - name: Deploy to ECS
        uses: aws-actions/amazon-ecs-deploy-task-definition@v1
        with:
          task-definition: ${{ steps.task-def.outputs.task-definition }}
          service: ${{ env.ECS_SERVICE }}
          cluster: ${{ env.ECS_CLUSTER }}
          wait-for-service-stability: true

      - name: Notify Slack
        if: always()
        uses: 8398a7/action-slack@v3
        with:
          status: ${{ job.status }}
          text: 'Backend deployment ${{ job.status }}'
          webhook_url: ${{ secrets.SLACK_WEBHOOK }}
```

**.github/workflows/deploy-ml-server.yml**:
```yaml
name: Deploy ML Server to AWS ECS

on:
  push:
    branches:
      - main
    paths:
      - 'charlie-parker-ml-server/**'

env:
  AWS_REGION: ap-northeast-2
  ECR_REPOSITORY: charlie-parker-ml-server
  ECS_CLUSTER: charlie-parker-cluster
  ECS_SERVICE: ml-server-service

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout code
        uses: actions/checkout@v3

      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v2
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-region: ${{ env.AWS_REGION }}

      - name: Login to Amazon ECR
        id: login-ecr
        uses: aws-actions/amazon-ecr-login@v1

      - name: Build and push Docker image
        env:
          ECR_REGISTRY: ${{ steps.login-ecr.outputs.registry }}
          IMAGE_TAG: ${{ github.sha }}
        run: |
          cd charlie-parker-ml-server
          docker build -t $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG .
          docker push $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG
          docker tag $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG $ECR_REGISTRY/$ECR_REPOSITORY:latest
          docker push $ECR_REGISTRY/$ECR_REPOSITORY:latest

      - name: Deploy to ECS
        run: |
          aws ecs update-service \
            --cluster ${{ env.ECS_CLUSTER }} \
            --service ${{ env.ECS_SERVICE }} \
            --force-new-deployment
```

---

### Week 5-6: 모니터링 및 로깅

#### 학습 목표
- CloudWatch 설정
- Application Insights
- 알람 설정

#### 1. Spring Boot Actuator 설정

**application.yml**:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: ${spring.application.name}
      environment: ${SPRING_PROFILES_ACTIVE:default}
```

#### 2. CloudWatch 로그 설정

**logback-spring.xml**:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>

    <!-- Console Appender -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss} - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- CloudWatch Appender -->
    <appender name="CLOUDWATCH" class="ca.pjer.logback.AwsLogsAppender">
        <layout>
            <pattern>[%thread] [%date] [%level] [%file:%line] - %msg%n</pattern>
        </layout>
        <logGroupName>/aws/ecs/charlie-parker/backend</logGroupName>
        <logStreamName>${HOSTNAME}</logStreamName>
        <logRegion>ap-northeast-2</logRegion>
    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="CLOUDWATCH"/>
    </root>
</configuration>
```

#### 3. Prometheus + Grafana 대시보드

**docker-compose.monitoring.yml**:
```yaml
version: '3.8'

services:
  prometheus:
    image: prom/prometheus:latest
    container_name: prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml
      - prometheus_data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'

  grafana:
    image: grafana/grafana:latest
    container_name: grafana
    ports:
      - "3001:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
    volumes:
      - grafana_data:/var/lib/grafana
      - ./grafana/dashboards:/etc/grafana/provisioning/dashboards
    depends_on:
      - prometheus

volumes:
  prometheus_data:
  grafana_data:
```

**prometheus/prometheus.yml**:
```yaml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'spring-boot-backend'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['backend:8080']

  - job_name: 'ml-server'
    metrics_path: '/metrics'
    static_configs:
      - targets: ['ml-server:8000']
```

---

### Week 7-8: 성능 최적화 및 포트폴리오

#### 학습 목표
- 병목 지점 분석 및 최적화
- 포트폴리오 문서화
- README 작성

#### 1. 성능 최적화 체크리스트

**Backend 최적화**:
```java
// 1. N+1 쿼리 해결
@EntityGraph(attributePaths = {"user", "subreddit"})
List<CommunityPostEntity> findAllWithUserAndSubreddit();

// 2. 페이징 처리
Page<CommunityPostEntity> findAll(Pageable pageable);

// 3. 캐싱
@Cacheable(value = "posts", key = "#postId")
public CommunityPostEntity getPost(Long postId) {
    return postRepository.findById(postId)
        .orElseThrow(() -> new PostNotFoundException());
}

// 4. 비동기 처리
@Async
public CompletableFuture<List<GenerationEntity>> getGenerationsAsync() {
    return CompletableFuture.completedFuture(
        generationRepository.findAll()
    );
}

// 5. 커넥션 풀 최적화
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
```

**ML Server 최적화**:
```python
# 1. 모델 로딩 최적화 (startup에서 한 번만)
@app.on_event("startup")
async def load_model():
    global model
    model = load_model_from_checkpoint()
    model.eval()
    torch.set_grad_enabled(False)  # Inference only

# 2. 배치 처리
async def generate_batch(requests: List[GenerationRequest]):
    # 여러 요청을 배치로 처리
    pass

# 3. 캐싱
from functools import lru_cache

@lru_cache(maxsize=100)
def get_cached_generation(params_hash: str):
    # 동일 파라미터 요청은 캐시에서 반환
    pass
```

#### 2. 포트폴리오 README

**README.md**:
```markdown
# 🎷 Charlie Parker AI - Jazz Improvisation Generator

> Charlie Parker 스타일의 Bebop 즉흥 연주를 생성하는 AI 서비스

[![Live Demo](https://img.shields.io/badge/demo-live-success)](https://charlie-parker.example.com)
[![Backend](https://img.shields.io/badge/backend-Spring%20Boot-green)](https://github.com/yourusername/charlie-parker-backend)
[![ML](https://img.shields.io/badge/ml-PyTorch-red)](https://github.com/yourusername/charlie-parker-ml)

## 📖 프로젝트 소개

Charlie Parker AI는 딥러닝 기술을 활용하여 전설적인 재즈 뮤지션 Charlie Parker의
Bebop 스타일 즉흥 연주를 생성하는 웹 서비스입니다.

### 주요 기능

- 🎵 **AI 즉흥 연주 생성**: Music Transformer로 Charlie Parker 스타일 멜로디 생성
- 🎹 **웹 MIDI 플레이어**: 브라우저에서 바로 재생 가능
- 📊 **Bebop 스타일 평가**: 생성된 연주의 스타일 유사도 분석
- 👥 **커뮤니티**: Reddit 스타일 커뮤니티로 생성물 공유

## 🏗️ 기술 스택

### Backend
- **Language**: Java 21
- **Framework**: Spring Boot 3.5.6
- **Database**: PostgreSQL 15
- **Cache**: Redis 7
- **Storage**: AWS S3
- **Infrastructure**: Docker, AWS ECS, Terraform

### ML Server
- **Framework**: FastAPI
- **ML**: PyTorch, Music Transformer
- **MIDI Processing**: pretty_midi, music21

### Frontend
- **Framework**: React 18
- **Audio**: Tone.js
- **UI**: Material-UI

### DevOps
- **CI/CD**: GitHub Actions
- **Monitoring**: CloudWatch, Prometheus, Grafana
- **IaC**: Terraform

## 🚀 Quick Start

### Prerequisites
- Docker & Docker Compose
- (Optional) NVIDIA GPU for ML inference

### 실행 방법

1. Repository 클론
```bash
git clone https://github.com/yourusername/charlie-parker-ai.git
cd charlie-parker-ai
```

2. 환경 변수 설정
```bash
cp .env.example .env
# .env 파일 수정
```

3. Docker Compose 실행
```bash
docker-compose up -d
```

4. 브라우저에서 접속
```
http://localhost:3000
```

## 📊 시스템 아키텍처

```
Frontend (React) → Backend (Spring Boot) → ML Server (FastAPI)
                         ↓                         ↓
                   PostgreSQL              Music Transformer
                         ↓
                      Redis
```

## 📈 성능 지표

- **생성 속도**: 평균 5초 (1000 토큰)
- **Bebop 스타일 점수**: 평균 0.75/1.00
- **API 응답 시간**: 평균 100ms (P95: 500ms)
- **동시 접속**: 최대 100명

## 🎯 프로젝트 하이라이트

### 1. 엔터프라이즈급 백엔드 아키텍처
- Layered Architecture (Controller → Service → Repository)
- Event-Driven Architecture (Spring Events)
- Multi-level Caching (Caffeine L1 + Redis L2)
- Distributed Lock (Redisson)

### 2. Music Transformer 구현
- Relative Position Encoding
- Self-Attention Mechanism
- Custom MIDI Tokenizer

### 3. 풀스택 개발
- Spring Boot ↔ FastAPI 통합
- React 프론트엔드
- Docker Compose 오케스트레이션

### 4. DevOps 실전 경험
- GitHub Actions CI/CD
- AWS ECS 배포
- Terraform IaC
- Prometheus + Grafana 모니터링

## 📝 학습 과정

이 프로젝트는 12개월간의 학습 과정의 결과물입니다:

1. **Phase 1**: Backend 기초 (Spring Boot, JPA, Redis)
2. **Phase 2**: 데이터 준비 (Charlie Parker MIDI 500+ 곡)
3. **Phase 3**: ML 모델 개발 (Music Transformer)
4. **Phase 4**: 서비스 통합 (Backend + ML + Frontend)
5. **Phase 5**: 프로덕션 배포 (AWS, CI/CD)

자세한 학습 과정: [CAREER_ROADMAP.md](docs/CAREER_ROADMAP.md)

## 🔗 관련 링크

- [Backend Repository](https://github.com/yourusername/charlie-parker-backend)
- [ML Server Repository](https://github.com/yourusername/charlie-parker-ml)
- [Frontend Repository](https://github.com/yourusername/charlie-parker-frontend)
- [API Documentation](https://charlie-parker.example.com/swagger-ui)
- [Live Demo](https://charlie-parker.example.com)

## 📧 Contact

- Email: ohhalim777@gmail.com
- GitHub: [@yourusername](https://github.com/yourusername)
- LinkedIn: [Your Name](https://linkedin.com/in/yourprofile)

## 📄 License

MIT License

---

**Made with ❤️ for Jazz and AI**
```

---

## 📊 Phase 5 평가 기준

### 인프라 구축 (30%)
- [ ] AWS 리소스 배포 (Terraform)
- [ ] ECS/Fargate 설정
- [ ] RDS, ElastiCache 구성

### CI/CD (25%)
- [ ] GitHub Actions 파이프라인
- [ ] 자동 테스트 및 배포
- [ ] 무중단 배포

### 모니터링 (20%)
- [ ] CloudWatch 로그 수집
- [ ] Prometheus + Grafana 대시보드
- [ ] 알람 설정

### 포트폴리오 (25%)
- [ ] README 작성
- [ ] 기술 블로그 포스팅 3개 이상
- [ ] 데모 영상 제작

---

## 🎯 취업 준비 체크리스트

### 포트폴리오
- [ ] GitHub README 완성
- [ ] 프로젝트 데모 영상 (5분)
- [ ] 아키텍처 다이어그램
- [ ] 기술 블로그 3개 이상 작성

### 이력서
- [ ] 프로젝트 상세 설명
- [ ] 사용 기술 스택 명시
- [ ] 성과 지표 (성능 개선, 사용자 수 등)

### 면접 준비
- [ ] 프로젝트 발표 자료 (PPT)
- [ ] 기술 질문 대비 (Spring, JPA, Redis, Docker 등)
- [ ] 코딩 테스트 준비 (알고리즘)

### 기술 블로그 주제 추천
1. "Music Transformer로 Charlie Parker 스타일 학습하기"
2. "Spring Boot와 FastAPI 통합: ML 모델 서빙 아키텍처"
3. "Redis + Caffeine 멀티레벨 캐싱 전략"
4. "GitHub Actions로 Spring Boot + PyTorch 자동 배포"
5. "Terraform으로 AWS ECS 인프라 구축하기"

---

## 💡 Phase 5 이후 발전 방향

### 단기 (1-3개월)
1. **기능 확장**
   - 사용자가 업로드한 멜로디 이어서 생성
   - 다양한 재즈 스타일 (Swing, Cool Jazz)
   - 화음 진행 생성

2. **성능 개선**
   - GPU 인스턴스 활용 (EC2 G4dn)
   - 모델 경량화 (Quantization)
   - 캐싱 전략 최적화

3. **사용자 경험**
   - 모바일 앱 (React Native)
   - 실시간 생성 (WebSocket)
   - 소셜 로그인 (Google, Apple)

### 중기 (3-6개월)
1. **비즈니스 모델**
   - 프리미엄 기능 (고품질 생성, 무제한 생성)
   - 구독 모델 ($5/월)
   - API 판매 (개발자용)

2. **마케팅**
   - Product Hunt 런칭
   - 재즈 커뮤니티 홍보
   - YouTube 튜토리얼

3. **파트너십**
   - 음악 교육 기관
   - 재즈 학원
   - 음악 스트리밍 서비스

### 장기 (6개월+)
1. **AI 연구**
   - 논문 작성 및 학회 발표
   - 오픈소스 공개
   - 교육 자료 제작

2. **스타트업 전환**
   - 투자 유치
   - 팀 구성
   - 사업자 등록

3. **글로벌 확장**
   - 다국어 지원
   - 글로벌 서버 (CloudFront)
   - 해외 마케팅

---

## 🏆 최종 목표

### 취업 목표
- **Backend Developer** 포지션
- 스타트업 또는 중견 IT 기업
- 연봉: 4,000만원+ (신입 기준)

### 장기 비전
- **ML Engineer** 전환
- 음악 AI 전문가
- 개인 프로젝트 → 스타트업

---

## 🎓 학습 리소스

### Backend
- [Baeldung - Spring Boot Tutorials](https://www.baeldung.com/spring-boot)
- [우아한형제들 기술 블로그](https://techblog.woowahan.com/)
- [토스 Tech](https://toss.tech/)

### DevOps
- [AWS Well-Architected Framework](https://aws.amazon.com/architecture/well-architected/)
- [Terraform Tutorials](https://learn.hashicorp.com/terraform)
- [Docker Documentation](https://docs.docker.com/)

### Machine Learning
- [Hugging Face Course](https://huggingface.co/course)
- [Fast.ai](https://course.fast.ai/)
- [Papers with Code](https://paperswithcode.com/)

### 커뮤니티
- [인프런 - 백엔드 개발자 커뮤니티](https://www.inflearn.com/)
- [OKKY - 개발자 커뮤니티](https://okky.kr/)
- [GeekNews](https://news.hada.io/)

---

**축하합니다! 🎉**

Phase 5까지 완료하면 다음을 달성하게 됩니다:

✅ 엔터프라이즈급 백엔드 아키텍처 경험
✅ AI/ML 모델 개발 및 서빙 경험
✅ 풀스택 개발 경험
✅ DevOps 및 클라우드 배포 경험
✅ 포트폴리오 완성
✅ 취업 경쟁력 확보

**이제 여러분은 백엔드 개발자이자 Music AI 엔지니어입니다!**

Charlie Parker처럼 즉흥적이면서도 체계적인 개발자가 되세요! 🎷
