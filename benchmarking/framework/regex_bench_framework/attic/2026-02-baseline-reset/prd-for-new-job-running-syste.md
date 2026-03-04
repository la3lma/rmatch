# Product Requirements Document: Robust Regex Benchmark Job Execution System

## 1. Executive Summary

This PRD outlines the design for a new job execution system to replace the current fragile benchmark runner that has suffered from multiple process conflicts, data fragmentation, and inconsistent long-running job execution.

## 2. Problem Statement

### Current System Issues Identified:
- **Multiple Competing Processes**: Jobs frequently spawn multiple concurrent processes, violating the "one job at a time" rule
- **Data Fragmentation**: Results split across multiple run_ids, making progress tracking difficult
- **Stale Job States**: Jobs stuck in RUNNING state when processes crash, requiring manual cleanup
- **No Process Coordination**: Lack of proper distributed locking mechanism for job execution
- **Poor Visibility**: Live reports don't accurately reflect running vs completed jobs
- **Inflexible Targeting**: Cannot easily run specific engines (e.g., "only rmatch") or test subsets
- **Fragile Long-Running Jobs**: 1000+ pattern tests with 1GB corpora fail due to process conflicts

## 3. Goals and Objectives

### Primary Goals:
1. **Zero Process Conflicts**: Guarantee only one benchmark job runs at any time
2. **Reliable Long-Running Jobs**: Enable 24+ hour scaling tests without interruption
3. **Consistent Data**: Eliminate run fragmentation and ensure all results are properly tracked
4. **Real-Time Visibility**: Accurate live status of what's running, queued, and completed
5. **Flexible Targeting**: Easy specification of engine subsets and test case filtering

### Success Metrics:
- Zero instances of multiple concurrent jobs
- 99.9% job completion rate for long-running tests
- <30 second recovery time from executor failures
- Real-time status accuracy within 5 seconds

## 4. Architecture Overview

### Component Architecture:

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   Web Control   │    │   Job Database   │    │ Executor Nodes  │
│    Service      │◄──►│   (SQLite/PG)   │◄──►│  (Workers)      │
│                 │    │                  │    │                 │
│  • Job Queue    │    │ • Job Queue      │    │ • Single Job    │
│  • Status API   │    │ • Locking Table  │    │ • Health Check  │
│  • Live Reports │    │ • Audit Log      │    │ • Result Upload │
└─────────────────┘    └──────────────────┘    └─────────────────┘
```

### Core Components:

#### 4.1 Web Control Service
- **Technology**: Python FastAPI or Flask
- **Responsibilities**:
  - Job queue management and prioritization
  - Distributed locking coordination
  - Real-time status API
  - Live progress web interface
  - Job targeting and filtering
  - Consistency monitoring and remediation

#### 4.2 Job Database (Enhanced)
- **Current**: SQLite (maintain compatibility)
- **Enhancements**:
  - Add `job_locks` table for distributed coordination
  - Add `executor_heartbeats` table for health monitoring
  - Add `audit_log` table for all state transitions
  - Implement database-level constraints for consistency

#### 4.3 Executor Nodes
- **Technology**: Python worker processes
- **Responsibilities**:
  - Acquire exclusive job locks via database transaction
  - Execute single benchmark job with engine isolation
  - Continuous heartbeat and consistency checking
  - Graceful failure handling and cleanup

## 5. Functional Requirements

### 5.1 Job Coordination (Critical)

**REQ-1.1**: Database Transaction Lock
- Each executor must acquire exclusive lock via database transaction before starting job
- Lock includes: `executor_id`, `job_id`, `acquired_at`, `heartbeat_expiry`
- Failed lock acquisition must result in executor waiting, not starting duplicate job

**REQ-1.2**: Heartbeat Mechanism
- Running executors must update heartbeat every 30 seconds
- Control service monitors heartbeats and marks missing executors as failed
- Jobs from failed executors automatically returned to PENDING state

**REQ-1.3**: Consistency Enforcement
- Control service runs consistency checks every 60 seconds
- Violations (multiple RUNNING jobs, stale locks) trigger immediate remediation
- All remediation actions logged to audit table

### 5.2 Job Targeting and Filtering

**REQ-2.1**: Engine-Specific Execution
- Support `--engines rmatch` to run only specified engines
- Support `--exclude-engines java-native-unfair` to skip engines
- Multiple engines via comma separation: `--engines rmatch,re2j`

**REQ-2.2**: Test Case Filtering
- Support pattern count filtering: `--pattern-counts 100,1000`
- Support corpus size filtering: `--corpus-sizes 1GB`
- Support iteration targeting: `--iterations 0,1` (for testing)

**REQ-2.3**: Resume and Retry Logic
- `--resume-only`: Only pick up existing PENDING jobs, create no new ones
- `--retry-failed`: Reset FAILED jobs to PENDING for retry
- `--max-runtime 14400`: Automatic timeout for runaway jobs

### 5.3 Real-Time Status and Monitoring

**REQ-3.1**: Live Status API
```json
GET /api/status
{
  "jobs": {
    "total": 720,
    "completed": 279,
    "running": 1,
    "queued": 440,
    "failed": 0
  },
  "current_job": {
    "job_id": "java-native-unfair|1000|1MB|1",
    "started_at": "2026-01-01T14:34:55Z",
    "runtime_seconds": 1847,
    "executor_id": "worker-01",
    "estimated_completion": "2026-01-01T16:45:00Z"
  },
  "executors": {
    "active": 1,
    "last_heartbeat": "2026-01-01T15:05:42Z"
  }
}
```

**REQ-3.2**: Live Web Dashboard
- Auto-refresh every 15 seconds
- Show current job with progress estimation
- Display job queue with priorities
- Real-time executor health status
- Interactive job filtering and search

**REQ-3.3**: Progress Estimation
- Calculate ETA based on similar completed jobs (same engine + pattern count)
- Factor in corpus size scaling for time estimates
- Display confidence intervals for estimates

## 6. Non-Functional Requirements

### 6.1 Reliability
- **Zero Data Loss**: All job results must be persisted before marking complete
- **Crash Recovery**: System must recover from any single component failure within 60 seconds
- **Long-Running Stability**: Support 48+ hour continuous operation without restart

### 6.2 Performance
- **Job Startup Time**: <10 seconds from PENDING to RUNNING state transition
- **Status Update Latency**: Real-time status updates within 5 seconds
- **Database Concurrency**: Handle 10+ concurrent executor health checks

### 6.3 Maintainability
- **Backward Compatibility**: Existing job database schema must remain functional
- **Clear Logging**: All state transitions logged with timestamps and context
- **Health Diagnostics**: Built-in commands to check system consistency

## 7. User Stories

### 7.1 Data Scientist (Primary User)
**Story 1**: "As a data scientist, I want to run only rmatch tests after an optimization, so I can quickly validate performance improvements without waiting for all engines."

**Story 2**: "As a data scientist, I want to see real-time progress of long-running 1000+ pattern tests, so I can estimate when results will be available."

**Story 3**: "As a data scientist, I want the system to recover automatically from crashed jobs, so I don't lose hours of computation time."

### 7.2 System Administrator
**Story 4**: "As an admin, I want to monitor system health via web dashboard, so I can ensure continuous operation without manual intervention."

**Story 5**: "As an admin, I want automatic consistency checking, so data corruption issues are detected and fixed immediately."

## 8. Detailed Requirements

### 8.1 Database Schema Enhancements

```sql
-- New table: job_locks for distributed coordination
CREATE TABLE job_locks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    job_id INTEGER NOT NULL,
    executor_id TEXT NOT NULL,
    acquired_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    heartbeat_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    FOREIGN KEY (job_id) REFERENCES benchmark_jobs(id),
    UNIQUE(job_id)  -- Ensures only one lock per job
);

-- New table: executor_heartbeats for health monitoring
CREATE TABLE executor_heartbeats (
    executor_id TEXT PRIMARY KEY,
    last_heartbeat TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status TEXT DEFAULT 'active',  -- active, idle, failed
    current_job_id INTEGER,
    system_info JSON,
    FOREIGN KEY (current_job_id) REFERENCES benchmark_jobs(id)
);

-- New table: audit_log for all state transitions
CREATE TABLE audit_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    event_type TEXT NOT NULL,  -- job_started, job_completed, executor_failed, etc.
    job_id INTEGER,
    executor_id TEXT,
    old_state TEXT,
    new_state TEXT,
    details JSON
);
```

### 8.2 API Specifications

#### Control Service REST API

```yaml
# Job Management
POST /api/jobs/start
  body: {engines: [str], pattern_counts: [int], corpus_sizes: [str]}

GET /api/jobs/status
  response: {total, completed, running, queued, failed}

POST /api/jobs/stop
  body: {job_id: int}

# Executor Management
POST /api/executors/register
  body: {executor_id: str, capabilities: {}}

GET /api/executors/health
  response: [{executor_id, status, last_heartbeat, current_job}]

# Real-time Status
GET /api/live/status
  response: {jobs, current_job, executors, queue}

WebSocket /ws/live
  real-time status updates
```

### 8.3 Command Line Interface

```bash
# Start control service
regex-bench control-service --port 8080 --database results/jobs.db

# Start executor node
regex-bench executor --control-url http://localhost:8080 --executor-id worker-01

# Submit jobs with targeting
regex-bench submit --engines rmatch --pattern-counts 100,1000 --corpus-sizes 1GB

# Monitor status
regex-bench status --live
regex-bench executors --health-check
```

## 9. Implementation Phases

### Phase 1: Foundation (Week 1-2)
- [ ] Implement database schema enhancements
- [ ] Create basic web control service with job queue API
- [ ] Build executor framework with heartbeat mechanism
- [ ] Implement distributed locking via database transactions

### Phase 2: Job Execution (Week 3-4)
- [ ] Integrate executors with existing benchmark engine code
- [ ] Implement job targeting and filtering logic
- [ ] Add consistency checking and automatic remediation
- [ ] Build comprehensive logging and audit trail

### Phase 3: User Interface (Week 5-6)
- [ ] Create live web dashboard with real-time updates
- [ ] Implement WebSocket for real-time status streaming
- [ ] Add progress estimation and ETA calculations
- [ ] Build CLI tools for job management

### Phase 4: Production Hardening (Week 7-8)
- [ ] Extensive testing with long-running jobs (48+ hours)
- [ ] Failure injection testing and recovery validation
- [ ] Performance optimization for high job throughput
- [ ] Documentation and deployment procedures

## 10. Risk Assessment

### High Risk Items:
1. **Database Contention**: Multiple executors competing for locks could cause deadlocks
   - *Mitigation*: Implement timeout-based lock acquisition with exponential backoff

2. **WebSocket Scaling**: Real-time updates may not scale beyond 10+ concurrent users
   - *Mitigation*: Implement SSE fallback and connection pooling

3. **Legacy Compatibility**: Existing job data must remain accessible
   - *Mitigation*: Implement database migration scripts with rollback capability

### Medium Risk Items:
1. **Executor Isolation**: Benchmark engines may still interfere with each other
2. **Network Partitions**: Executors may lose connection to control service
3. **Resource Exhaustion**: Long-running jobs may consume all system resources

## 11. Success Criteria

### Launch Criteria:
- [ ] Zero instances of multiple concurrent jobs in 48-hour test period
- [ ] Successfully complete 10 consecutive 1000+ pattern benchmark runs
- [ ] Real-time dashboard shows accurate status within 5-second SLA
- [ ] Job targeting functionality works for all engine combinations
- [ ] Automatic recovery from executor failures within 60 seconds

### Post-Launch Metrics (30 days):
- Job completion rate >99.5%
- Mean time between failures >168 hours (1 week)
- User satisfaction score >4.5/5 for reliability improvements

## 12. Open Questions

1. **Horizontal Scaling**: Should we support multiple executor nodes on different machines?
2. **Job Priority**: Should high-value tests (1000+ patterns) get priority over smaller tests?
3. **Resource Management**: Should we implement CPU/memory limits per executor?
4. **Result Storage**: Should we implement result compression for large-scale benchmarks?
5. **Integration**: How should this integrate with existing analytics and reporting tools?

---

**Document Version**: 1.0
**Last Updated**: January 1, 2026
**Author**: Technical Team
**Status**: Draft for Review