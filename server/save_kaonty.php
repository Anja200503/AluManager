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

$d = json_decode(file_get_contents('php://input'), true);
if (!$d) { echo json_encode(['success' => false, 'error' => 'Donnees invalides']); exit; }

$op_id  = mysqli_real_escape_string($conn, $d['op_id'] ?? '');
$cat    = mysqli_real_escape_string($conn, $d['cat'] ?? 'alu');
$type   = mysqli_real_escape_string($conn, $d['type'] ?? 'in');
$amount = floatval($d['amount'] ?? 0);
$label  = mysqli_real_escape_string($conn, $d['label'] ?? '');
$date   = mysqli_real_escape_string($conn, $d['date'] ?? date('Y-m-d'));
$ts     = intval($d['ts'] ?? 0);
if ($op_id === '') { echo json_encode(['success' => false, 'error' => 'op_id requis']); exit; }

$sql = "INSERT INTO kaonty (op_id,cat,type,amount,label,op_date,ts)
        VALUES ('$op_id','$cat','$type',$amount,'$label','$date',$ts)
        ON DUPLICATE KEY UPDATE cat='$cat',type='$type',amount=$amount,label='$label',op_date='$date',ts=$ts";
if (mysqli_query($conn, $sql)) echo json_encode(['success' => true]);
else echo json_encode(['success' => false, 'error' => mysqli_error($conn)]);
mysqli_close($conn);
?>
