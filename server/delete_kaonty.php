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

$d = json_decode(file_get_contents('php://input'), true);
$op_id = mysqli_real_escape_string($conn, $d['op_id'] ?? '');
if ($op_id === '') { echo json_encode(['success' => false, 'error' => 'op_id requis']); exit; }
if (mysqli_query($conn, "DELETE FROM kaonty WHERE op_id='$op_id'")) echo json_encode(['success' => true]);
else echo json_encode(['success' => false, 'error' => mysqli_error($conn)]);
mysqli_close($conn);
?>
