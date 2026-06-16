<?php
header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');

$DB_USER = 'if0_41439729';
$DB_PASS = '2QGGvCVRYHgeUS';
$DB_NAME = 'if0_41439729_command';
$DB_PORT = 3306;
$hosts   = ['sql211.infinityfree.com', 'localhost', '127.0.0.1'];

$conn = null;
foreach ($hosts as $h) { $conn = @mysqli_connect($h, $DB_USER, $DB_PASS, $DB_NAME, $DB_PORT); if ($conn) { mysqli_set_charset($conn, 'utf8mb4'); break; } }
if (!$conn) { echo json_encode(['success' => false, 'error' => 'Connexion impossible']); exit; }

mysqli_query($conn, "CREATE TABLE IF NOT EXISTS kaonty (
  id INT(11) NOT NULL AUTO_INCREMENT,
  op_id VARCHAR(40) NOT NULL,
  cat VARCHAR(20) NOT NULL,
  type VARCHAR(5) NOT NULL,
  amount DECIMAL(15,2) NOT NULL DEFAULT 0,
  label VARCHAR(200),
  op_date DATE NOT NULL,
  ts BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_opid (op_id)
) ENGINE=MyISAM DEFAULT CHARSET=utf8mb4");

$date  = isset($_GET['date']) ? mysqli_real_escape_string($conn, $_GET['date']) : '';
$where = $date !== '' ? "WHERE op_date='$date'" : '';
$rs = mysqli_query($conn, "SELECT op_id,cat,type,amount,label,op_date,ts FROM kaonty $where ORDER BY ts DESC");
$ops = [];
while ($r = mysqli_fetch_assoc($rs)) $ops[] = $r;
echo json_encode(['success' => true, 'ops' => $ops], JSON_UNESCAPED_UNICODE);
mysqli_close($conn);
?>
