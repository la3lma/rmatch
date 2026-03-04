"""
Corpus generation and management.
"""

import random
import string
import datetime
import ipaddress
from typing import Dict, Any, List
from pathlib import Path


class CorpusManager:
    """Generate and manage test corpora."""

    def generate_corpus(self, size: str, config: Dict[str, Any]) -> str:
        """Generate a corpus of the specified size."""
        # Parse size string (e.g., "1MB", "100KB")
        size_bytes = self._parse_size(size)
        corpus_type = config.get('type', 'synthetic_controllable')

        if corpus_type == 'synthetic_controllable' or corpus_type == 'synthetic':
            return self._generate_synthetic_corpus(size_bytes, config)
        elif corpus_type == 'logs':
            return self._generate_logs_corpus(size_bytes, config)
        elif corpus_type == 'natural_language':
            return self._generate_natural_language_corpus(size_bytes, config)
        else:
            return self._generate_generic_corpus(size_bytes)

    def save_corpus_to_file(self, corpus_content: str, filepath: Path) -> None:
        """Save corpus content to a file."""
        filepath.parent.mkdir(parents=True, exist_ok=True)
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(corpus_content)

    def generate_corpus_files(self, sizes: List[str], types: List[str], output_dir: Path) -> Dict[str, Path]:
        """Generate all corpus files for given sizes and types."""
        generated_files = {}

        for corpus_type in types:
            for size in sizes:
                config = {'type': corpus_type}
                corpus_content = self.generate_corpus(size, config)

                filename = f"corpus_{corpus_type}_{size}.txt"
                filepath = output_dir / filename

                self.save_corpus_to_file(corpus_content, filepath)
                generated_files[f"{corpus_type}_{size}"] = filepath

        return generated_files

    def _parse_size(self, size: str) -> int:
        """Parse size string to bytes."""
        size = size.upper().strip()

        if size.endswith('KB'):
            return int(size[:-2]) * 1024
        elif size.endswith('MB'):
            return int(size[:-2]) * 1024 * 1024
        elif size.endswith('GB'):
            return int(size[:-2]) * 1024 * 1024 * 1024
        else:
            # Assume bytes
            return int(size)

    def _generate_synthetic_corpus(self, size_bytes: int, config: Dict[str, Any]) -> str:
        """Generate a synthetic corpus with controllable characteristics."""
        match_density = config.get('match_density', 'medium')
        line_count_target = config.get('line_count_target', {})
        character_distribution = config.get('character_distribution', 'log_like')

        lines = []
        current_size = 0

        # Generate lines until we reach target size
        line_templates = self._get_line_templates(character_distribution, match_density)

        while current_size < size_bytes:
            line = random.choice(line_templates)

            # Add some randomization
            line = self._randomize_line(line)

            lines.append(line)
            current_size += len(line) + 1  # +1 for newline

        return '\n'.join(lines)

    def _generate_generic_corpus(self, size_bytes: int) -> str:
        """Generate a simple generic corpus."""
        lines = []
        current_size = 0

        sample_lines = [
            "This is a sample log line with timestamp 2024-12-19 12:30:45",
            "ERROR: Connection failed to server 192.168.1.100",
            "INFO: User john.doe@example.com logged in successfully",
            "WARN: High memory usage detected: 85%",
            "DEBUG: Processing request /api/v1/users with method GET",
            "Connection established to database server",
            "Request processed in 125ms",
            "Cache hit ratio: 94.5%"
        ]

        while current_size < size_bytes:
            line = random.choice(sample_lines)
            lines.append(line)
            current_size += len(line) + 1

        return '\n'.join(lines)

    def _generate_logs_corpus(self, size_bytes: int, config: Dict[str, Any]) -> str:
        """Generate realistic log data with timestamps, IPs, URLs, etc."""
        lines = []
        current_size = 0

        # Common log levels
        log_levels = ['INFO', 'WARN', 'ERROR', 'DEBUG', 'TRACE', 'FATAL']

        # Common services/components
        services = ['auth', 'api', 'db', 'cache', 'worker', 'scheduler', 'proxy', 'gateway']

        # HTTP status codes
        status_codes = [200, 201, 204, 301, 302, 400, 401, 403, 404, 405, 500, 502, 503]

        # Common endpoints
        endpoints = [
            '/api/v1/users', '/api/v1/auth/login', '/api/v1/auth/logout',
            '/api/v2/data/search', '/api/v1/admin/stats', '/health',
            '/metrics', '/status', '/api/v1/upload', '/static/css/main.css',
            '/static/js/app.js', '/favicon.ico'
        ]

        # User agents
        user_agents = [
            'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
            'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15',
            'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36',
            'curl/7.68.0',
            'PostmanRuntime/7.28.4'
        ]

        # Generate log entries
        base_time = datetime.datetime(2025, 12, 19, 10, 0, 0)

        while current_size < size_bytes:
            # Generate timestamp
            timestamp = base_time + datetime.timedelta(
                seconds=random.randint(0, 86400),
                microseconds=random.randint(0, 999999)
            )
            ts_str = timestamp.strftime('%Y-%m-%d %H:%M:%S.%f')[:-3]

            log_type = random.choice(['application', 'access', 'error', 'system'])

            if log_type == 'application':
                line = self._generate_application_log(ts_str, log_levels, services)
            elif log_type == 'access':
                line = self._generate_access_log(ts_str, endpoints, status_codes, user_agents)
            elif log_type == 'error':
                line = self._generate_error_log(ts_str, services)
            else:  # system
                line = self._generate_system_log(ts_str)

            lines.append(line)
            current_size += len(line) + 1  # +1 for newline

        return '\n'.join(lines)

    def _generate_application_log(self, timestamp: str, log_levels: List[str], services: List[str]) -> str:
        """Generate application log entry."""
        level = random.choice(log_levels)
        service = random.choice(services)

        templates = [
            f"{timestamp} {level} [{service}] Processing request from client {self._random_ip()}",
            f"{timestamp} {level} [{service}] Database query executed in {random.randint(1, 500)}ms",
            f"{timestamp} {level} [{service}] Cache hit ratio: {random.randint(85, 99)}%",
            f"{timestamp} {level} [{service}] Memory usage: {random.randint(45, 90)}% ({random.randint(100, 2000)}MB)",
            f"{timestamp} {level} [{service}] Connection pool: {random.randint(1, 20)} active, {random.randint(0, 5)} idle",
            f"{timestamp} {level} [{service}] Session created for user {self._random_username()}@{self._random_domain()}",
            f"{timestamp} {level} [{service}] Task {random.randint(1000, 9999)} completed successfully",
            f"{timestamp} {level} [{service}] Rate limit exceeded for IP {self._random_ip()}"
        ]

        return random.choice(templates)

    def _generate_access_log(self, timestamp: str, endpoints: List[str], status_codes: List[int], user_agents: List[str]) -> str:
        """Generate HTTP access log entry."""
        ip = self._random_ip()
        method = random.choice(['GET', 'POST', 'PUT', 'DELETE', 'PATCH'])
        endpoint = random.choice(endpoints)
        if endpoint.startswith('/api/v1/users'):
            endpoint += f"/{random.randint(1, 10000)}"
        status = random.choice(status_codes)
        size_bytes = random.randint(200, 50000)
        response_time = random.randint(10, 2000)
        user_agent = random.choice(user_agents)

        # Common log format with additional fields
        return f'{ip} - - [{timestamp}] "{method} {endpoint} HTTP/1.1" {status} {size_bytes} {response_time}ms "{user_agent}"'

    def _generate_error_log(self, timestamp: str, services: List[str]) -> str:
        """Generate error log entry."""
        service = random.choice(services)

        error_types = [
            "ConnectionTimeoutException",
            "DatabaseConnectionException",
            "ValidationError",
            "AuthenticationFailedException",
            "FileNotFoundException",
            "MemoryLimitExceededException",
            "RateLimitException"
        ]

        error_type = random.choice(error_types)

        templates = [
            f"{timestamp} ERROR [{service}] {error_type}: Connection to database failed after 30 seconds",
            f"{timestamp} ERROR [{service}] {error_type}: Invalid credentials for user {self._random_username()}",
            f"{timestamp} ERROR [{service}] {error_type}: File not found: /var/log/{service}/{random.randint(1000, 9999)}.log",
            f"{timestamp} ERROR [{service}] {error_type} at line {random.randint(1, 1000)} in {service}.py",
            f"{timestamp} ERROR [{service}] {error_type}: Request timeout from {self._random_ip()}"
        ]

        return random.choice(templates)

    def _generate_system_log(self, timestamp: str) -> str:
        """Generate system log entry."""
        templates = [
            f"{timestamp} kernel: CPU {random.randint(0, 7)} usage: {random.randint(0, 100)}%",
            f"{timestamp} systemd[1]: Started service {random.choice(['nginx', 'postgresql', 'redis', 'elasticsearch'])}",
            f"{timestamp} sshd[{random.randint(1000, 9999)}]: Failed login attempt from {self._random_ip()}",
            f"{timestamp} cron[{random.randint(1000, 9999)}]: (root) CMD (/usr/bin/cleanup.sh)",
            f"{timestamp} NetworkManager: Connected to WiFi network 'Corporate-{random.randint(1, 10)}'",
            f"{timestamp} docker[{random.randint(1000, 9999)}]: Container {self._random_container_id()} started"
        ]

        return random.choice(templates)

    def _random_ip(self) -> str:
        """Generate a random IP address."""
        # Mix of private and public IPs
        if random.random() < 0.7:
            # Private IP ranges
            ranges = [
                (10, 0, 0, 0, 8),
                (172, 16, 0, 0, 12),
                (192, 168, 0, 0, 16)
            ]
            range_info = random.choice(ranges)
            if range_info[0] == 10:
                return f"10.{random.randint(0, 255)}.{random.randint(0, 255)}.{random.randint(1, 254)}"
            elif range_info[0] == 172:
                return f"172.{random.randint(16, 31)}.{random.randint(0, 255)}.{random.randint(1, 254)}"
            else:
                return f"192.168.{random.randint(0, 255)}.{random.randint(1, 254)}"
        else:
            # Public IP (avoiding reserved ranges)
            return f"{random.randint(1, 223)}.{random.randint(0, 255)}.{random.randint(0, 255)}.{random.randint(1, 254)}"

    def _random_username(self) -> str:
        """Generate a random username."""
        first_names = ['john', 'jane', 'bob', 'alice', 'charlie', 'diana', 'eve', 'frank', 'grace', 'henry']
        last_names = ['smith', 'doe', 'johnson', 'brown', 'davis', 'miller', 'wilson', 'moore', 'taylor', 'anderson']

        if random.random() < 0.8:
            return f"{random.choice(first_names)}.{random.choice(last_names)}"
        else:
            return f"{random.choice(first_names)}{random.randint(1, 999)}"

    def _random_domain(self) -> str:
        """Generate a random domain name."""
        domains = ['example.com', 'company.org', 'test.net', 'demo.io', 'corp.com', 'dev.local']
        return random.choice(domains)

    def _random_container_id(self) -> str:
        """Generate a random container ID."""
        return ''.join(random.choices(string.ascii_lowercase + string.digits, k=12))

    def _generate_natural_language_corpus(self, size_bytes: int, config: Dict[str, Any]) -> str:
        """Generate natural language text corpus."""
        paragraphs = []
        current_size = 0

        # Sample sentences with various patterns
        sentence_templates = [
            "The {adjective} {noun} {verb} {adverb} through the {location}.",
            "In {year}, the {organization} decided to {action} their {object}.",
            "Every {frequency}, we {verb} the {process} to ensure {quality}.",
            "The {person} from {place} {verb} about {topic} during {timeperiod}.",
            "According to {source}, the {measurement} has {changed} by {percentage}% since {date}.",
            "When {condition}, the {system} automatically {action} the {component}.",
            "The research team at {institution} discovered that {finding}.",
            "Between {starttime} and {endtime}, approximately {number} {items} were {processed}.",
            "Due to {reason}, the {department} has {implemented} new {policies}.",
            "Users can {action} their {settings} by {method} the {interface}."
        ]

        # Word pools for template replacement
        adjectives = ['efficient', 'robust', 'scalable', 'reliable', 'innovative', 'comprehensive', 'advanced', 'sophisticated']
        nouns = ['system', 'algorithm', 'framework', 'application', 'service', 'platform', 'interface', 'database']
        verbs = ['processes', 'analyzes', 'optimizes', 'manages', 'coordinates', 'facilitates', 'executes', 'monitors']
        adverbs = ['efficiently', 'seamlessly', 'automatically', 'continuously', 'systematically', 'dynamically']
        locations = ['cloud environment', 'data center', 'distributed network', 'server cluster', 'edge device']

        organizations = ['Microsoft', 'Google', 'Amazon', 'Meta', 'Apple', 'IBM', 'Oracle', 'Salesforce']
        actions = ['upgrade', 'migrate', 'modernize', 'restructure', 'optimize', 'streamline', 'consolidate']
        objects = ['infrastructure', 'architecture', 'workflow', 'codebase', 'deployment', 'monitoring']

        while current_size < size_bytes:
            # Generate a paragraph with 3-8 sentences
            sentences_in_para = random.randint(3, 8)
            paragraph_sentences = []

            for _ in range(sentences_in_para):
                template = random.choice(sentence_templates)
                sentence = self._fill_natural_language_template(template, {
                    'adjective': adjectives,
                    'noun': nouns,
                    'verb': verbs,
                    'adverb': adverbs,
                    'location': locations,
                    'organization': organizations,
                    'action': actions,
                    'object': objects,
                    'year': [str(y) for y in range(2020, 2026)],
                    'frequency': ['day', 'week', 'month', 'quarter', 'year'],
                    'process': ['data', 'workflow', 'pipeline', 'validation', 'deployment'],
                    'quality': ['accuracy', 'performance', 'reliability', 'security', 'compliance'],
                    'person': ['developer', 'analyst', 'manager', 'engineer', 'researcher'],
                    'place': ['Silicon Valley', 'Boston', 'Seattle', 'Austin', 'New York'],
                    'topic': ['machine learning', 'cloud computing', 'cybersecurity', 'data science'],
                    'timeperiod': ['the conference', 'Q3 review', 'the summit', 'the workshop'],
                    'source': ['industry reports', 'recent studies', 'market analysis', 'user feedback'],
                    'measurement': ['performance', 'efficiency', 'throughput', 'latency', 'accuracy'],
                    'changed': ['increased', 'decreased', 'improved', 'declined', 'fluctuated'],
                    'percentage': [str(p) for p in range(5, 95, 5)],
                    'date': ['2024', 'last year', 'Q4', 'the beginning of 2025'],
                    'condition': ['load increases', 'errors occur', 'maintenance starts', 'alerts trigger'],
                    'system': ['monitoring system', 'backup service', 'security protocol', 'load balancer'],
                    'component': ['alerts', 'backups', 'failover', 'scaling', 'cleanup'],
                    'institution': ['Stanford', 'MIT', 'Carnegie Mellon', 'UC Berkeley', 'Georgia Tech'],
                    'finding': ['performance scales linearly', 'memory usage optimizes automatically', 'latency decreases with caching'],
                    'starttime': ['9 AM', 'midnight', 'peak hours', 'business hours'],
                    'endtime': ['5 PM', '6 AM', 'off-peak hours', 'end of day'],
                    'number': [str(n) for n in [100, 500, 1000, 5000, 10000, 50000]],
                    'items': ['requests', 'transactions', 'files', 'records', 'messages'],
                    'processed': ['handled', 'processed', 'completed', 'verified', 'archived'],
                    'reason': ['security updates', 'compliance requirements', 'user feedback', 'performance issues'],
                    'department': ['IT team', 'security group', 'development team', 'operations team'],
                    'implemented': ['introduced', 'deployed', 'established', 'activated', 'enforced'],
                    'policies': ['procedures', 'protocols', 'guidelines', 'standards', 'frameworks'],
                    'settings': ['preferences', 'configurations', 'parameters', 'options', 'profiles'],
                    'method': ['accessing', 'navigating to', 'clicking on', 'selecting', 'editing'],
                    'interface': ['dashboard', 'control panel', 'settings page', 'admin console', 'configuration menu']
                })

                paragraph_sentences.append(sentence)

            paragraph = ' '.join(paragraph_sentences)
            paragraphs.append(paragraph)
            current_size += len(paragraph) + 2  # +2 for double newline between paragraphs

        return '\n\n'.join(paragraphs)

    def _fill_natural_language_template(self, template: str, word_pools: Dict[str, List[str]]) -> str:
        """Fill a template with random words from pools."""
        import re

        def replacer(match):
            key = match.group(1)
            if key in word_pools:
                return random.choice(word_pools[key])
            return match.group(0)

        return re.sub(r'\{(\w+)\}', replacer, template)

    def _get_line_templates(self, distribution: str, match_density: str) -> list:
        """Get line templates based on character distribution and match density."""
        if distribution == 'log_like':
            templates = [
                "2024-12-19 12:30:{:02d} INFO [main] Processing request from 192.168.1.{}",
                "2024-12-19 12:30:{:02d} ERROR [worker] Failed to connect to database",
                "2024-12-19 12:30:{:02d} DEBUG [auth] User authentication successful for user{}",
                "GET /api/v1/users/{} HTTP/1.1 200 {}ms",
                "POST /login HTTP/1.1 401 Unauthorized",
                "Connection pool size: {} active, {} idle",
                "Memory usage: {}MB / {}MB ({}%)",
                "Cache statistics: {} hits, {} misses, {:.1f}% hit rate"
            ]
        else:
            # ASCII-only fallback
            templates = [
                "Sample text line {} with some content",
                "Another line containing data item {}",
                "Log entry number {} with status OK",
                "Processing item {} completed successfully"
            ]

        return templates

    def _randomize_line(self, template: str) -> str:
        """Add randomization to a line template."""
        # Simple randomization - replace {} with random values
        import re

        def replace_placeholder(match):
            return str(random.randint(1, 999))

        # Replace numeric placeholders
        line = re.sub(r'\{\}', replace_placeholder, template)

        # Replace format placeholders like {:02d}
        line = re.sub(r'\{:[\d\.]*[sd]\}', replace_placeholder, line)

        return line